import { createContext, useCallback, useContext, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '@/api/endpoints';
import { sessionStore, toSession, type SessionUser } from '@/api/session';
import type { LoginRequest, RegisterRequest } from '@/api/types';

/*
 * useAuth() — who is logged in, plus login / register / logout.
 *
 * useSyncExternalStore subscribes React to sessionStore, so when the
 * session changes ANYWHERE (a refresh in client.ts, a logout in another
 * tab) every component re-renders with the new user.
 */

interface AuthValue {
  user: SessionUser | null;
  login: (body: LoginRequest) => Promise<SessionUser>;
  register: (body: RegisterRequest) => Promise<SessionUser>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.get, sessionStore.get);
  const user = session?.user ?? null;

  const login = useCallback(
    async (body: LoginRequest) => {
      const s = toSession(await authApi.login(body));
      queryClient.clear(); // never show the previous user's cached data
      sessionStore.set(s);
      return s.user;
    },
    [queryClient],
  );

  const register = useCallback(
    async (body: RegisterRequest) => {
      const s = toSession(await authApi.register(body));
      queryClient.clear();
      sessionStore.set(s);
      return s.user;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    const current = sessionStore.get();
    sessionStore.clear();
    queryClient.clear();
    if (current) {
      // best effort: even if this fails, the user is logged out locally
      await authApi.logout(current.refreshToken).catch(() => undefined);
    }
  }, [queryClient]);

  const logoutAll = useCallback(async () => {
    await authApi.logoutAll();
    sessionStore.clear();
    queryClient.clear();
  }, [queryClient]);

  const value = useMemo(() => ({ user, login, register, logout, logoutAll }), [user, login, register, logout, logoutAll]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
