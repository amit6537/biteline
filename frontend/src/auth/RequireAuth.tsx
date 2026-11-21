import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import type { Role } from '@/api/types';
import { useAuth } from './AuthContext';
import { Button, EmptyState } from '@/components/ui';
import { ROLE_HOME, ROLE_LABEL } from '@/lib/roles';
import { ShieldAlert } from 'lucide-react';

/** Not logged in → /login?next=<this page>. Wrong role → a friendly message. */
export function RequireAuth({ children, roles }: { children: ReactNode; roles?: Role[] }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return (
      <EmptyState
        icon={ShieldAlert}
        title="Not available for this account"
        text={`You're logged in as a ${ROLE_LABEL[user.role].toLowerCase()}. This page is for another kind of account.`}
        action={<Link to={ROLE_HOME[user.role]}><Button variant="secondary">Go to my dashboard</Button></Link>}
      />
    );
  }
  return <>{children}</>;
}
