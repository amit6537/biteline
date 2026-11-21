import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CircleCheck, UserPlus } from 'lucide-react';
import { adminApi } from '@/api/endpoints';
import type { StuckOrder } from '@/api/types';
import { useToast } from '@/components/Toast';
import { Badge, Button, Card, EmptyState, ErrorState, Input, PageHeader, Skeleton } from '@/components/ui';
import { time } from '@/lib/format';

/*
 * /admin — operations: READY orders that no rider has taken.
 * The backend retries assignment every 30 s by itself; this is the manual
 * override when nobody is free nearby.
 */
export function AdminPage() {
  const stuck = useQuery({ queryKey: ['admin', 'unassigned'], queryFn: adminApi.unassigned, refetchInterval: 15_000 });

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Operations" subtitle="Ready orders still waiting for a rider · refreshes every 15 s" />
      {stuck.isLoading ? (
        <div className="space-y-3">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-24" />)}</div>
      ) : stuck.error ? (
        <ErrorState error={stuck.error} onRetry={() => stuck.refetch()} />
      ) : !stuck.data?.length ? (
        <EmptyState icon={CircleCheck} title="All clear" text="Every ready order has a rider." />
      ) : (
        <div className="space-y-3">
          {stuck.data.map((o) => <StuckRow key={o.orderId} o={o} />)}
        </div>
      )}
    </div>
  );
}

function StuckRow({ o }: { o: StuckOrder }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [partnerId, setPartnerId] = useState('');
  const assign = useMutation({
    mutationFn: () => adminApi.assign(o.orderId, partnerId.trim()),
    onSuccess: (a) => {
      toast.success(`Assigned to ${a.partnerName}.`);
      qc.invalidateQueries({ queryKey: ['admin'] });
    },
    onError: toast.error,
  });

  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <Link to={`/orders/${o.orderId}`} className="font-semibold hover:underline">
            #{o.orderId.slice(0, 6).toUpperCase()} · {o.restaurantName}
          </Link>
          <p className="text-[13px] text-muted">Ready since {time(o.readyAt)}</p>
        </div>
        <Badge tone={o.waitingMinutes >= 10 ? 'danger' : 'warning'}>
          {o.alerted && <AlertTriangle size={12} />} waiting {o.waitingMinutes} min
        </Badge>
      </div>
      <form
        className="mt-4 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          assign.mutate();
        }}
      >
        <div className="flex-1">
          <Input value={partnerId} onChange={(e) => setPartnerId(e.target.value)} placeholder="Delivery partner ID (UUID)" aria-label="Partner ID" />
        </div>
        <Button type="submit" icon={UserPlus} loading={assign.isPending} disabled={partnerId.trim().length < 32}>
          Assign
        </Button>
      </form>
    </Card>
  );
}
