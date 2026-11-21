import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from '@/api/client';
import { AuthProvider } from '@/auth/AuthContext';
import { ConfirmProvider } from '@/components/Confirm';
import { ToastProvider } from '@/components/Toast';
import { App } from './App';
import './styles/index.css';

/*
 * TanStack Query = the server-data cache.
 * useQuery(['cart']) anywhere in the app shares ONE request and ONE copy.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      // Don't retry errors that won't fix themselves (400/401/403/404/409/429)
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <ConfirmProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </ConfirmProvider>
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
);
