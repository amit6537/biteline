import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { Toaster, toast } from 'sonner';
import { ApiError } from '@/api/client';

/*
 * Toasts via Sonner (small library: stacking, swipe-to-dismiss, smooth
 * enter/exit). The app keeps using our own tiny API:
 *     const toast = useToast();  toast.success('Saved.');  toast.error(err);
 * so swapping the library later only touches this file.
 */

interface ToastApi {
  success: (message: string) => void;
  error: (error: unknown) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/** Any thrown value → a sentence a user can read. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const api = useMemo<ToastApi>(
    () => ({
      success: (m) => toast.success(m),
      error: (e) => toast.error(errorMessage(e), { duration: 6000 }),
      info: (m) => toast(m),
    }),
    [],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <Toaster
        position="bottom-center"
        visibleToasts={3}
        closeButton
        // phones: sit above the bottom tab bar (64px + safe area)
        mobileOffset={{ bottom: 'calc(76px + env(safe-area-inset-bottom))' }}
        toastOptions={{
          classNames: {
            toast:
              '!rounded-md !border !border-line !bg-bg !text-fg !shadow-[var(--shadow-float)] !font-sans !text-sm !gap-3 !px-4 !py-3.5',
            success: '[&_[data-icon]]:!text-success',
            error: '[&_[data-icon]]:!text-danger',
            closeButton: '!bg-bg !border-line !text-muted hover:!text-fg',
          },
        }}
      />
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>');
  return ctx;
}
