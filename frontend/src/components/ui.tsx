import { forwardRef, useState, type CSSProperties, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Link } from 'react-router';
import { ChevronLeft, Loader2, Minus, Plus, Star, UtensilsCrossed, type LucideIcon } from 'lucide-react';
import type { OrderStatus } from '@/api/types';
import { cn, STATUS_LABEL } from '@/lib/format';
import { pickImage } from '@/lib/images';

/* ── Button ─────────────────────────────────────────────────── */

type Variant = 'primary' | 'dark' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'lg';

const variants: Record<Variant, string> = {
  // DESIGN.md button-primary: brand fill, white label, pressed = deeper red, disabled = pale tint
  primary: 'bg-primary text-white hover:bg-primary-active active:bg-primary-active disabled:bg-primary-disabled disabled:text-white',
  // ink fill — for strong but non-brand actions (e.g. a selected state)
  dark: 'bg-fg text-white hover:bg-black disabled:bg-surface-3 disabled:text-subtle',
  // button-secondary: white, ink label, 1px ink outline
  secondary: 'bg-bg text-fg border border-fg hover:bg-surface-2 disabled:border-line disabled:text-subtle',
  // button-tertiary-text: plain ink label
  ghost: 'text-fg hover:bg-surface-2 disabled:text-subtle',
  danger: 'bg-bg text-danger border border-danger/40 hover:bg-danger/5 disabled:opacity-50',
};

const sizes: Record<Size, string> = {
  sm: 'h-9 px-3.5 text-sm rounded-sm gap-1.5',
  md: 'h-11 px-5 text-[15px] rounded-sm gap-2',
  lg: 'h-12 px-6 text-base rounded-sm gap-2',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: LucideIcon;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, icon: Icon, className, children, disabled, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={cn(
        'pressable inline-flex shrink-0 select-none items-center justify-center font-medium transition-[background-color,border-color,color,transform] disabled:cursor-not-allowed disabled:active:scale-100',
        variants[variant],
        sizes[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Loader2 size={16} className="animate-spin" /> : Icon ? <Icon size={size === 'sm' ? 14 : 16} /> : null}
      {children}
    </button>
  );
});

/* ── Form fields ────────────────────────────────────────────── */

// DESIGN.md text-input: white, 1px hairline, 8px radius; focus = 2px ink border, no glow
const fieldBase =
  'w-full rounded-sm border bg-bg px-3.5 text-base text-fg placeholder:text-subtle outline-none transition-[border-color,box-shadow] focus:border-fg focus:shadow-[inset_0_0_0_1px_var(--color-fg)] focus-visible:outline-none disabled:bg-surface-2 disabled:text-muted';

interface FieldProps {
  label?: string;
  hint?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & FieldProps>(function Input(
  { label, hint, error, className, id, ...rest },
  ref,
) {
  const inputId = id ?? rest.name;
  return (
    <label className="block" htmlFor={inputId}>
      {label && <span className="mb-1.5 block text-sm font-medium text-fg">{label}</span>}
      <input
        ref={ref}
        id={inputId}
        aria-invalid={!!error}
        className={cn(fieldBase, 'h-12', error ? 'border-danger' : 'border-line-strong', className)}
        {...rest}
      />
      {error ? (
        <span className="mt-1.5 block text-xs text-danger">{error}</span>
      ) : hint ? (
        <span className="mt-1.5 block text-xs text-subtle">{hint}</span>
      ) : null}
    </label>
  );
});

export function Textarea({
  label,
  error,
  className,
  ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & FieldProps) {
  return (
    <label className="block">
      {label && <span className="mb-1.5 block text-sm font-medium text-fg">{label}</span>}
      <textarea
        className={cn(fieldBase, 'min-h-20 resize-y py-3', error ? 'border-danger' : 'border-line-strong', className)}
        {...rest}
      />
      {error && <span className="mt-1.5 block text-xs text-danger">{error}</span>}
    </label>
  );
}

/* ── Surfaces ───────────────────────────────────────────────── */

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('rounded-md border border-line bg-surface', className)}>{children}</div>;
}

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';
const tones: Record<Tone, string> = {
  neutral: 'bg-surface-3 text-fg',
  success: 'bg-success/10 text-success',
  warning: 'bg-warning/10 text-warning',
  danger: 'bg-danger/10 text-danger',
  info: 'bg-info/10 text-info',
};

export function Badge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-full px-2.5 text-xs font-semibold', tones[tone], className)}>
      {children}
    </span>
  );
}

const statusTone: Record<OrderStatus, Tone> = {
  PLACED: 'info',
  ACCEPTED: 'info',
  PREPARING: 'warning',
  READY: 'warning',
  PICKED_UP: 'info',
  DELIVERED: 'success',
  CANCELLED: 'danger',
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={statusTone[status]}>{STATUS_LABEL[status]}</Badge>;
}

/* ── Feedback ───────────────────────────────────────────────── */

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('animate-spin text-muted', className)} size={20} aria-label="Loading" />;
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('skeleton rounded-md', className)} />;
}

export function EmptyState({
  icon: Icon,
  title,
  text,
  action,
}: {
  icon: LucideIcon;
  title: string;
  text?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center rounded-lg border border-line px-6 py-16 text-center">
      <div className="mb-4 grid size-14 place-items-center rounded-full bg-surface-3 text-fg">
        <Icon size={24} strokeWidth={1.75} />
      </div>
      <h3 className="text-xl font-semibold">{title}</h3>
      {text && <p className="mt-2 max-w-md text-[15px] leading-6 text-muted">{text}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Something went wrong.';
  return (
    <div className="rounded-md border border-danger/30 bg-danger/5 px-5 py-4 text-sm">
      <p className="text-fg">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-2 font-medium text-muted underline-offset-4 hover:text-fg hover:underline">
          Try again
        </button>
      )}
    </div>
  );
}

/* ── Small pieces ───────────────────────────────────────────── */

export function Rating({ value, count }: { value: number | null | undefined; count?: number | null }) {
  if (value == null || !count) return <span className="text-xs text-subtle">New</span>;
  return (
    <span className="inline-flex items-center gap-1 text-[13px] font-medium">
      <Star size={13} className="fill-fg text-fg" />
      {value.toFixed(1)}
      {count != null && <span className="font-normal text-muted">({count})</span>}
    </span>
  );
}

/** Interactive 1–5 stars */
export function StarInput({ value, onChange, label }: { value: number; onChange: (v: number) => void; label: string }) {
  return (
    <div>
      <span className="mb-1.5 block text-sm font-medium text-fg">{label}</span>
      <div className="flex gap-1" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n} star${n > 1 ? 's' : ''}`}
            onClick={() => onChange(n)}
            className="rounded-sm p-1 transition-transform hover:scale-110"
          >
            <Star size={26} className={n <= value ? 'fill-fg text-fg' : 'text-line-strong'} />
          </button>
        ))}
      </div>
    </div>
  );
}

/** Green square = veg, red = non-veg (the Indian food label) */
export function VegMark({ veg }: { veg: boolean }) {
  return (
    <span
      title={veg ? 'Vegetarian' : 'Non-vegetarian'}
      className={cn('inline-grid size-3.5 shrink-0 place-items-center rounded-[3px] border', veg ? 'border-veg' : 'border-nonveg')}
    >
      <span className={cn('size-1.5 rounded-full', veg ? 'bg-veg' : 'bg-nonveg')} />
    </span>
  );
}

export function QtyStepper({
  value,
  onChange,
  busy,
  min = 0,
}: {
  value: number;
  onChange: (next: number) => void;
  busy?: boolean;
  min?: number;
}) {
  return (
    <div className="inline-flex h-9 items-center gap-1">
      <button
        className="grid size-8 place-items-center rounded-full border border-line-strong text-fg hover:border-fg disabled:border-line disabled:text-subtle"
        onClick={() => onChange(value - 1)}
        disabled={busy || value <= min}
        aria-label="Decrease quantity"
      >
        <Minus size={15} />
      </button>
      <span className="w-6 text-center text-sm font-semibold tabular-nums">{busy ? <Loader2 size={14} className="mx-auto animate-spin" /> : value}</span>
      <button
        className="grid size-8 place-items-center rounded-full border border-line-strong text-fg hover:border-fg disabled:border-line disabled:text-subtle"
        onClick={() => onChange(value + 1)}
        disabled={busy || value >= 20}
        aria-label="Increase quantity"
      >
        <Plus size={15} />
      </button>
    </div>
  );
}

/**
 * Restaurant / dish image.
 *   src set        → that photo
 *   src missing    → a fitting stock photo picked from `hint` (name, cuisine…)
 *   photo fails    → a calm placeholder with a dish icon
 */
export function Thumb({
  src,
  name,
  hint,
  className,
  width = 800,
  seed,
  style,
}: {
  src: string | null | undefined;
  name: string;
  hint?: string | null;
  className?: string;
  width?: number;
  /** e.g. an id: chooses between several fitting photos */
  seed?: string;
  /** e.g. { viewTransitionName } so the photo can morph between pages */
  style?: CSSProperties;
}) {
  const url = src || pickImage(`${name} ${hint ?? ''}`, width, seed);
  const [broken, setBroken] = useState<string | null>(null);

  if (broken !== url) {
    return (
      <img
        src={url}
        alt=""
        loading="lazy"
        decoding="async"
        style={style}
        onError={() => setBroken(url)}
        className={cn('bg-surface-2 object-cover', className)}
      />
    );
  }
  return (
    <div
      aria-hidden
      title={name}
      className={cn('grid place-items-center bg-[radial-gradient(120%_90%_at_30%_20%,var(--color-surface-3),var(--color-surface)_70%)] text-line-strong', className)}
    >
      <UtensilsCrossed size={28} strokeWidth={1.5} />
    </div>
  );
}

export function PageHeader({ title, subtitle, back, action }: { title: string; subtitle?: ReactNode; back?: string; action?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        {back && (
          <Link
            to={back}
            viewTransition
            aria-label="Back"
            className="mb-4 grid size-9 place-items-center rounded-full bg-surface-3 text-fg transition-colors hover:bg-line"
          >
            <ChevronLeft size={18} />
          </Link>
        )}
        <h1 className="text-[26px] font-semibold leading-tight sm:text-[28px]">{title}</h1>
        {subtitle && <p className="mt-1.5 text-[15px] text-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

