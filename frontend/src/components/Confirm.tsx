import { createContext, lazy, Suspense, useCallback, useContext, useRef, useState, type ReactNode } from 'react';

// the dialog UI + Radix load on the first confirm, not with the page
const ConfirmDialog = lazy(() => import('./ConfirmDialog'));

/*
 * Replaces window.confirm() with a styled, accessible dialog:
 *
 *     const confirm = useConfirm();
 *     if (await confirm({ title: 'Delete dish?', confirmLabel: 'Delete', danger: true })) remove();
 *
 * Radix AlertDialog (in ConfirmDialog.tsx, loaded on first use) gives focus trapping, Esc to cancel and screen-reader
 * roles for free. Styling (.overlay / .dialog in index.css) makes it a
 * bottom sheet on phones and a centred card on bigger screens.
 */

export interface ConfirmOptions {
  title: string;
  text?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** red confirm button for destructive actions */
  danger?: boolean;
}

type Ask = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Ask | null>(null);

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const [open, setOpen] = useState(false);
  const resolver = useRef<(ok: boolean) => void>(undefined);

  const ask = useCallback<Ask>((o) => {
    resolver.current?.(false); // a second ask cancels the first
    setOptions(o);
    setOpen(true);
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const finish = (ok: boolean) => {
    resolver.current?.(ok);
    resolver.current = undefined;
    setOpen(false);
  };

  return (
    <ConfirmContext.Provider value={ask}>
      {children}
      {options && (
        <Suspense fallback={null}>
          <ConfirmDialog options={options} open={open} finish={finish} />
        </Suspense>
      )}
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): Ask {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm must be used inside <ConfirmProvider>');
  return ctx;
}
