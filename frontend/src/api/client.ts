import { sessionStore, toSession } from './session';
import type { AuthResponse } from './types';

/*
 * client.ts — every HTTP call to the backend goes through request().
 *
 * What it does for you:
 *   1. Adds "Authorization: Bearer <access token>" when logged in.
 *   2. Access token expired (401)? → calls /api/auth/refresh ONCE,
 *      saves the new tokens, and repeats the original request.
 *      If 5 requests fail at the same moment, they all wait for the SAME
 *      refresh (see refreshSession) — the backend only accepts each refresh
 *      token once, so 5 parallel refreshes would log the user out.
 *   3. Turns the backend's error JSON {status, message, fieldErrors} into
 *      an ApiError you can show on screen.
 */

export const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;
  /** Seconds, from the Retry-After header on 429 responses. */
  readonly retryAfter?: number;

  constructor(status: number, message: string, fieldErrors?: Record<string, string>, retryAfter?: number) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.retryAfter = retryAfter;
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface RequestOptions {
  body?: unknown;
  /** false = never send the token (login, register…) */
  auth?: boolean;
  signal?: AbortSignal;
}

export async function request<T>(method: Method, path: string, opts: RequestOptions = {}): Promise<T> {
  const { auth = true } = opts;
  let res = await send(method, path, opts, auth);

  if (res.status === 401 && auth && sessionStore.get()) {
    const refreshed = await refreshSession();
    if (refreshed) {
      res = await send(method, path, opts, auth); // once, with the new token
    }
  }
  return parse<T>(res);
}

async function send(method: Method, path: string, opts: RequestOptions, auth: boolean): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  const token = auth ? sessionStore.get()?.accessToken : undefined;
  if (token) headers.Authorization = `Bearer ${token}`;

  try {
    return await fetch(API_URL + path, {
      method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      signal: opts.signal,
    });
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e;
    throw new ApiError(0, "Can't reach the server. Is the backend running on " + API_URL + '?');
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;

  const text = await res.text();
  let data: unknown = undefined;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = undefined;
    }
  }

  if (!res.ok) {
    const body = (data ?? {}) as { message?: string; fieldErrors?: Record<string, string> };
    const retryAfter = Number(res.headers.get('Retry-After')) || undefined;
    throw new ApiError(res.status, body.message ?? defaultMessage(res.status), body.fieldErrors, retryAfter);
  }
  return data as T;
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Please log in again.';
  if (status === 403) return "You don't have permission to do that.";
  if (status === 404) return 'Not found.';
  if (status === 429) return 'Too many attempts. Please wait a moment.';
  if (status >= 500) return 'Something went wrong on our side. Please try again.';
  return 'Request failed.';
}

// ── Token refresh (single-flight) ────────────────────────────

let inflight: Promise<boolean> | null = null;

/** Get new tokens. Returns true if the session is usable afterwards. */
export function refreshSession(): Promise<boolean> {
  if (!inflight) {
    inflight = doRefresh().finally(() => {
      inflight = null;
    });
  }
  return inflight;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function doRefresh(): Promise<boolean> {
  const session = sessionStore.reload();
  if (!session) return false;
  const used = session.refreshToken;

  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken: used }),
    });
  } catch {
    return false; // offline: keep the session, the user can retry
  }

  if (res.ok) {
    sessionStore.set(toSession((await res.json()) as AuthResponse));
    return true;
  }

  // Another tab may have used this same refresh token a moment earlier
  // (the backend answers 401 "already used" to the slower one). If that tab
  // has saved newer tokens by now, just use them.
  for (let i = 0; i < 3; i++) {
    const latest = sessionStore.reload();
    if (latest && latest.refreshToken !== used) return true;
    await sleep(300);
  }

  if (res.status === 401) sessionStore.clear(); // session really ended → logged out
  return false;
}
