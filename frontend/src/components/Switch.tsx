import * as SwitchPrimitive from '@radix-ui/react-switch';
import { cn } from '@/lib/format';

/* Own file on purpose: only owner/rider pages use it, so it ships in their (lazy) chunks. */
/** On/off switch (Radix: keyboard + screen-reader "switch" role). The knob slides. */
export function Switch({
  checked,
  onCheckedChange,
  disabled,
  label,
  className,
}: {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  disabled?: boolean;
  label: string;
  className?: string;
}) {
  return (
    <SwitchPrimitive.Root
      checked={checked}
      onCheckedChange={onCheckedChange}
      disabled={disabled}
      aria-label={label}
      title={label}
      className={cn(
        'relative h-6 w-10 shrink-0 rounded-full transition-colors duration-200 disabled:opacity-50',
        'bg-line-strong data-[state=checked]:bg-fg',
        className,
      )}
    >
      <SwitchPrimitive.Thumb className="block size-5 translate-x-0.5 rounded-full bg-white shadow-sm transition-transform duration-200 ease-[var(--ease-out-soft)] data-[state=checked]:translate-x-[18px]" />
    </SwitchPrimitive.Root>
  );
}

