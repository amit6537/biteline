import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { Bike, CircleCheck, MailCheck, ShoppingBag, Store, UtensilsCrossed } from 'lucide-react';
import { ApiError } from '@/api/client';
import { authApi } from '@/api/endpoints';
import type { RegisterRequest } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { Button, Input } from '@/components/ui';
import { cn } from '@/lib/format';
import { ROLE_HOME } from '@/lib/roles';

/** Only allow redirects inside this app ("/orders"), never to another site. */
function safeNext(next: string | null) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}

/** DESIGN.md login dialog: a white card, a centred header bar, generous padding. */
function AuthLayout({ title, subtitle, children, footer, bar }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode; bar?: string }) {
  return (
    <div className="mx-auto flex min-h-[calc(100dvh-16rem)] max-w-[568px] flex-col justify-center py-4">
      <div className="overflow-hidden rounded-lg border border-line bg-bg shadow-[var(--shadow-float)]">
        <div className="flex h-16 items-center justify-center gap-2 border-b border-line px-6">
          <span className="grid size-7 place-items-center rounded-sm bg-primary text-white">
            <UtensilsCrossed size={14} strokeWidth={2.5} />
          </span>
          <span className="text-base font-bold">{bar ?? title}</span>
        </div>
        <div className="p-6 sm:p-8">
          <h1 className="text-[22px] font-semibold leading-tight">{title}</h1>
          <p className="mb-6 mt-1.5 text-[15px] text-muted">{subtitle}</p>
          {children}
        </div>
      </div>
      <p className="mt-6 text-center text-sm text-muted">{footer}</p>
    </div>
  );
}

function FormError({ error }: { error: string | null }) {
  if (!error) return null;
  return (
    <p role="alert" className="rounded-sm border border-danger/30 bg-danger/5 px-3 py-2.5 text-sm text-danger">
      {error}
    </p>
  );
}

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const justReset = params.get('reset') === '1';

  const [email, setEmail] = useState(params.get('email') ?? '');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (user) return <Navigate to={next === '/' ? ROLE_HOME[user.role] : next} replace />;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const u = await login({ email: email.trim(), password });
      navigate(next === '/' ? ROLE_HOME[u.role] : next, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Login failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      bar="Log in"
      title="Welcome back to Food Delivery"
      subtitle="Log in to order, track deliveries and see your history."
      footer={
        <>
          New here?{' '}
          <Link to={`/register${next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`} className="font-medium text-fg hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      {justReset && (
        <p role="status" className="mb-4 flex items-center gap-2 rounded-sm border border-success/30 bg-success/8 px-3 py-2.5 text-sm">
          <CircleCheck size={16} className="shrink-0 text-success" /> Password changed. Log in with your new password.
        </p>
      )}
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Input label="Email" name="email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
        <div>
          <Input label="Password" name="password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
          <Link
            to={`/forgot-password${email.trim() ? `?email=${encodeURIComponent(email.trim())}` : ''}`}
            className="mt-2 inline-block text-sm font-semibold underline underline-offset-2"
          >
            Forgot password?
          </Link>
        </div>
        <FormError error={error} />
        <Button type="submit" size="lg" className="w-full" loading={busy} disabled={!email || !password}>
          Log in
        </Button>
      </form>
    </AuthLayout>
  );
}

const ROLES: { value: NonNullable<RegisterRequest['role']>; label: string; icon: typeof Store; text: string }[] = [
  { value: 'CUSTOMER', label: 'Order food', icon: ShoppingBag, text: 'Customer' },
  { value: 'RESTAURANT_OWNER', label: 'Run a kitchen', icon: Store, text: 'Restaurant' },
  { value: 'DELIVERY_PARTNER', label: 'Deliver', icon: Bike, text: 'Rider' },
];

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));

  const [form, setForm] = useState<RegisterRequest>({ name: '', email: '', phone: '', password: '', role: 'CUSTOMER' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  if (user) return <Navigate to={next === '/' ? ROLE_HOME[user.role] : next} replace />;

  const set = (k: keyof RegisterRequest) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      const u = await register({ ...form, name: form.name.trim(), email: form.email.trim(), phone: form.phone.trim() });
      navigate(next === '/' ? ROLE_HOME[u.role] : next, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors ?? {});
        setError(err.fieldErrors ? null : err.message);
      } else {
        setError('Sign up failed.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      bar="Sign up"
      title="Create your account"
      subtitle="One account for ordering, running a restaurant or delivering."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-fg hover:underline">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-fg">I want to</legend>
          <div className="grid grid-cols-3 gap-2">
            {ROLES.map(({ value, label, icon: Icon, text }) => {
              const active = form.role === value;
              return (
                <button
                  key={value}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setForm((f) => ({ ...f, role: value }))}
                  className={cn(
                    'flex flex-col items-start gap-2 rounded-md border p-3 text-left transition-colors',
                    active ? 'border-fg bg-surface-2 shadow-[inset_0_0_0_1px_var(--color-fg)]' : 'border-line bg-bg hover:border-fg',
                  )}
                >
                  <Icon size={18} className={active ? 'text-fg' : 'text-muted'} />
                  <span>
                    <span className="block text-[13px] font-medium leading-4">{label}</span>
                    <span className="block text-xs text-muted">{text}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </fieldset>

        <Input label="Full name" name="name" autoComplete="name" value={form.name} onChange={set('name')} error={fieldErrors.name} placeholder="Amit Sharma" />
        <Input label="Email" name="email" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={fieldErrors.email} placeholder="you@example.com" />
        <Input label="Phone" name="phone" type="tel" inputMode="numeric" autoComplete="tel-national" maxLength={10} value={form.phone} onChange={set('phone')} error={fieldErrors.phone} placeholder="9876543210" hint="10-digit mobile number" />
        <Input label="Password" name="password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')} error={fieldErrors.password} placeholder="At least 8 characters" />
        <FormError error={error} />
        <Button type="submit" size="lg" className="w-full" loading={busy}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}

// ── Forgot / reset password ──────────────────────────────────

/* /forgot-password — ask for a reset link by email */
export function ForgotPasswordPage() {
  const [params] = useSearchParams();
  const [email, setEmail] = useState(params.get('email') ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword(email.trim());
      setSentTo(email.trim());
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors?.email) setError(err.fieldErrors.email);
      else setError(err instanceof ApiError ? err.message : 'Could not send the email.');
    } finally {
      setBusy(false);
    }
  }

  if (sentTo) {
    return (
      <AuthLayout
        title="Check your email"
        subtitle={`If an account exists for ${sentTo}, we've sent a link to reset the password. It works once, for 30 minutes.`}
        footer={<Link to="/login" className="font-medium text-fg hover:underline">Back to log in</Link>}
      >
        <div className="space-y-4">
          <p className="flex gap-3 rounded-md border border-line bg-surface px-4 py-3 text-sm text-muted">
            <MailCheck size={18} className="mt-0.5 shrink-0 text-success" />
            <span>
              No email? Check spam, or wait a minute and try again.
              {import.meta.env.DEV && (
                <>
                  {' '}Running locally: emails land in{' '}
                  <a href="http://localhost:8025" target="_blank" rel="noreferrer" className="font-medium text-fg underline-offset-4 hover:underline">
                    Mailpit
                  </a>
                  .
                </>
              )}
            </span>
          </p>
          <Button variant="secondary" className="w-full" onClick={() => setSentTo(null)}>
            Use a different email
          </Button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="Forgot password?"
      subtitle="Enter your account email and we'll send you a link to choose a new password."
      footer={<>Remembered it? <Link to="/login" className="font-medium text-fg hover:underline">Log in</Link></>}
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Input label="Email" name="email" type="email" autoComplete="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
        <FormError error={error} />
        <Button type="submit" size="lg" className="w-full" loading={busy} disabled={!email.trim()}>
          Send reset link
        </Button>
      </form>
    </AuthLayout>
  );
}

/* /reset-password?token=… — the link from the email */
export function ResetPasswordPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [linkDead, setLinkDead] = useState(false);

  const tooShort = password.length > 0 && password.length < 8;
  const mismatch = confirm.length > 0 && confirm !== password;

  if (!token || linkDead) {
    return (
      <AuthLayout
        title="This link doesn't work"
        subtitle={error ?? 'The reset link is missing, already used or expired. Links work once, for 30 minutes.'}
        footer={<Link to="/login" className="font-medium text-fg hover:underline">Back to log in</Link>}
      >
        <Link to="/forgot-password">
          <Button size="lg" className="w-full">Send a new link</Button>
        </Link>
      </AuthLayout>
    );
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (password.length < 8 || password !== confirm) return;
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword(token, password);
      // every session was ended by the backend → also forget this tab's one
      if (user) await logout();
      navigate('/login?reset=1', { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && !err.fieldErrors) {
        setError(err.message);
        setLinkDead(true);
      } else if (err instanceof ApiError && err.fieldErrors?.newPassword) {
        setError(err.fieldErrors.newPassword);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not reset the password.');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout
      title="Choose a new password"
      subtitle="After this you'll be logged out on every device, then you can log in with the new password."
      footer={<Link to="/login" className="font-medium text-fg hover:underline">Back to log in</Link>}
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Input label="New password" name="new-password" type="password" autoComplete="new-password" autoFocus value={password} onChange={(e) => setPassword(e.target.value)} placeholder="At least 8 characters" error={tooShort ? 'At least 8 characters' : undefined} />
        <Input label="Confirm new password" name="confirm-password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} placeholder="Type it again" error={mismatch ? "Passwords don't match" : undefined} />
        <FormError error={error} />
        <Button type="submit" size="lg" className="w-full" loading={busy} disabled={password.length < 8 || password !== confirm}>
          Save new password
        </Button>
      </form>
    </AuthLayout>
  );
}
