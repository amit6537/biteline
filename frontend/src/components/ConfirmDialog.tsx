import * as AlertDialog from '@radix-ui/react-alert-dialog';
import type { ConfirmOptions } from './Confirm';
import { Button } from './ui';

/*
 * The confirm dialog itself (Radix AlertDialog ≈ 14 kB). Loaded lazily by
 * ConfirmProvider the first time something asks for confirmation, so it is
 * not part of the first page load.
 */
export default function ConfirmDialog({
  options,
  open,
  finish,
}: {
  options: ConfirmOptions | null;
  open: boolean;
  finish: (ok: boolean) => void;
}) {
  return (
    <AlertDialog.Root open={open} onOpenChange={(o) => !o && finish(false)}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="overlay fixed inset-0 z-50 bg-black/50" />
        <AlertDialog.Content
          className="dialog fixed inset-x-0 bottom-0 z-50 rounded-t-lg bg-bg p-6 pb-[calc(1.5rem+env(safe-area-inset-bottom))] shadow-[var(--shadow-float)] outline-none
                     sm:inset-0 sm:m-auto sm:h-fit sm:w-full sm:max-w-md sm:rounded-lg sm:pb-6"
        >
          {/* the little grab handle that says "sheet" on phones */}
          <div className="mx-auto -mt-2 mb-4 h-1 w-10 rounded-full bg-line sm:hidden" />
          <AlertDialog.Title className="text-[22px] font-semibold leading-tight">{options?.title}</AlertDialog.Title>
          {options?.text ? (
            <AlertDialog.Description className="mt-2 text-[15px] leading-6 text-muted">{options.text}</AlertDialog.Description>
          ) : (
            <AlertDialog.Description className="sr-only">Please confirm</AlertDialog.Description>
          )}
          <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <AlertDialog.Cancel asChild>
              <Button variant="ghost" size="lg" className="underline-offset-4 hover:underline">
                {options?.cancelLabel ?? 'Cancel'}
              </Button>
            </AlertDialog.Cancel>
            <AlertDialog.Action asChild>
              <Button variant={options?.danger ? 'primary' : 'dark'} size="lg" onClick={() => finish(true)}>
                {options?.confirmLabel ?? 'Confirm'}
              </Button>
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
