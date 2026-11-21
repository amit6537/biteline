import { Link, Navigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight, ReceiptText } from 'lucide-react';
import { orderApi } from '@/api/endpoints';
import { useAuth } from '@/auth/AuthContext';
import { Button, EmptyState, ErrorState, PageHeader, Skeleton, StatusBadge } from '@/components/ui';
import { Animated } from '@/components/Animated';
import { ACTIVE_STATUSES, dateTime, money } from '@/lib/format';
import { ROLE_HOME } from '@/lib/roles';

export function OrdersPage() {
  const { user } = useAuth();
  const orders = useQuery({
    queryKey: ['orders', 'mine'],
    queryFn: () => orderApi.mine(),
    enabled: user?.role === 'CUSTOMER',
    // keep the list fresh while something is on its way
    refetchInterval: (q) => (q.state.data?.content.some((o) => ACTIVE_STATUSES.includes(o.status)) ? 15_000 : false),
  });

  // Owners see orders on the kitchen board, riders on the deliveries screen
  if (user && user.role !== 'CUSTOMER') return <Navigate to={ROLE_HOME[user.role]} replace />;

  return (
    <div>
      <PageHeader title="Your orders" />
      {orders.isLoading ? (
        <div className="space-y-3">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-20" />)}</div>
      ) : orders.error ? (
        <ErrorState error={orders.error} onRetry={() => orders.refetch()} />
      ) : !orders.data?.content.length ? (
        <EmptyState icon={ReceiptText} title="No orders yet" text="Your orders will show up here." action={<Link to="/"><Button>Find food</Button></Link>} />
      ) : (
        <Animated as="ul" className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface">
          {orders.data.content.map((o) => (
            <li key={o.id}>
              <Link to={`/orders/${o.id}`} viewTransition className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-surface-2">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="truncate text-[15px] font-semibold">{o.restaurantName}</p>
                    <StatusBadge status={o.status} />
                  </div>
                  <p className="mt-1 truncate text-[13px] text-muted">
                    {o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}
                  </p>
                  <p className="mt-0.5 text-xs text-subtle">{dateTime(o.placedAt)}</p>
                </div>
                <span className="text-sm font-medium tabular-nums">{money(o.totalAmount)}</span>
                <ChevronRight size={18} className="text-subtle" />
              </Link>
            </li>
          ))}
        </Animated>
      )}
    </div>
  );
}
