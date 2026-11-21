import type { AuthResponse, Role, UUID } from './types';

/*
 * The logged-in session: tokens + who the user is.
 *
 * Kept in localStorage so a page reload keeps you logged in, and so every
 * open tab shares ONE session (when tab A refreshes the tokens, tab B sees
 * the new ones through the "storage" event below).
 *
 * Trade-off: JavaScript can read localStorage, so an XSS bug could steal the
 * tokens. The backend limits the damage (15-min access tokens, one-time
 * refresh tokens with reuse detection). A stricter setup would move the
 * refresh token into an httpOnly cookie.
 */

export interface SessionUser {
  userId: UUID;
  name: string;
  email: string;
  role: Role;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: SessionUser;
}

const KEY = 'fd.session';
type Listener = () => void;
const listeners = new Set<Listener>();

let cached: Session | null | undefined; // undefined = not read yet

function read(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

function emit() {
  listeners.forEach((l) => l());
}

export const sessionStore = {
  get(): Session | null {
    if (cached === undefined) cached = read();
    return cached;
  },

  set(session: Session) {
    cached = session;
    try {
      localStorage.setItem(KEY, JSON.stringify(session));
    } catch {
      /* private mode / storage full: session lives in memory only */
    }
    emit();
  },

  clear() {
    cached = null;
    try {
      localStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
    emit();
  },

  /** Re-read storage (another tab may have written newer tokens). */
  reload(): Session | null {
    cached = read();
    return cached;
  },

  subscribe(listener: Listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

// Another tab logged in / out / refreshed → follow it
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key === KEY || e.key === null) {
      cached = read();
      emit();
    }
  });
}

export function toSession(auth: AuthResponse): Session {
  return {
    accessToken: auth.accessToken,
    refreshToken: auth.refreshToken,
    user: { userId: auth.userId, name: auth.name, email: auth.email, role: auth.role },
  };
}
