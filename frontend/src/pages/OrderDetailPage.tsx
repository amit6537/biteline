import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bike, Check, ChefHat, CircleCheck, Clock, MapPin, Package, ReceiptText, Store, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import { orderApi, paymentApi } from '@/api/endpoints';
import type { Order, OrderStatus, Tracking } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { useToast } from '@/components/Toast';
import { OrderActions } from '@/pages/owner/OrderCard';
import { ROLE_HOME } from '@/lib/roles';
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, Skeleton, StarInput, StatusBadge, Textarea } from '@/components/ui';
import { ACTIVE_STATUSES, cn, dateTime, km, money, relative, time } from '@/lib/format';

const STEPS: { status: OrderStatus; label: string; icon: typeof Check }[] = [
  { status: 'PLACED', label: 'Placed', icon: ReceiptText },
  { status: 'ACCEPTED', label: 'Accepted', icon: Store },
  { status: 'PREPARING', label: 'Preparing', icon: ChefHat },
  { status: 'READY', label: 'Ready', icon: Package },
  { status: 'PICKED_UP', label: 'On the way', icon: Bike },
  { status: 'DELIVERED', label: 'Delivered', icon: CircleCheck },
];

export function OrderDetailPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const role = user?.role ?? 'CUSTOMER';
  const backTo = role === 'CUSTOMER' ? '/orders' : ROLE_HOME[role];
  const order = useQuery({
    queryKey: ['order', id],
    queryFn: () => orderApi.get(id),
    refetchInterval: (q) => (q.state.data && ACTIVE_STATUSES.includes(q.state.data.status) ? 10_000 : false),
  });

  if (order.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-40" />
        <Skeleton className="h-60" />
      </div>
    );
  }
  if (order.error) {
    const e = order.error;
    if (e instanceof ApiError && (e.status === 404 || e.status === 403)) {
      return <EmptyState icon={ReceiptText} title="Order not found" action={<Link to={backTo}><Button variant="secondary">Go back</Button></Link>} />;
    }
    return <ErrorState error={e} onRetry={() => order.refetch()} />;
  }
  const o = order.data!;
  const active = ACTIVE_STATUSES.includes(o.status);

  return (
    <div>
      <PageHeader
        back={backTo}
        title={role === 'CUSTOMER' ? o.restaurantName : `${o.customerName} · ${o.restaurantName}`}
        subtitle={<>Order #{o.id.slice(0, 8)} · {dateTime(o.placedAt)}</>}
        action={<StatusBadge status={o.status} />}
      />

      <div className="grid gap-8 grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_380px]">
        <div className="space-y-6">
          {o.status === 'CANCELLED' ? (
            <Card className="border-danger/30 p-5">
              <p className="flex items-center gap-2 font-semibold"><X size={18} className="text-danger" /> Order cancelled</p>
              <p className="mt-1 text-sm text-muted">
                {o.cancellationReason ?? 'No reason given'} · {relative(o.cancelledAt)}
              </p>
            </Card>
          ) : (
            <>
              {active && <LiveTracking orderId={o.id} />}
              <Timeline order={o} />
            </>
          )}
          {o.status === 'DELIVERED' && role === 'CUSTOMER' && <ReviewForm order={o} />}
        </div>

        <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
          {(role === 'RESTAURANT_OWNER' || role === 'ADMIN') && (
            <Card className="p-4">
              <OrderActions order={o} />
            </Card>
          )}
          <Bill order={o} />
          <Card className="p-5">
            <p className="flex items-start gap-3 text-sm">
              <MapPin size={17} className="mt-0.5 shrink-0 text-muted" />
              <span className="text-muted">{o.deliveryAddressText}</span>
            </p>
            {o.specialInstructions && <p className="mt-3 border-t border-line pt-3 text-[13px] text-muted">“{o.specialInstructions}”</p>}
          </Card>
          {role === 'CUSTOMER' && (o.status === 'PLACED' || o.status === 'ACCEPTED') && <CancelOrder order={o} />}
        </aside>
      </div>
    </div>
  );
}

function LiveTracking({ orderId }: { orderId: string }) {
  const tracking = useQuery({
    queryKey: ['tracking', orderId],
    queryFn: () => orderApi.tracking(orderId),
    refetchInterval: 10_000,
  });
  const t: Tracking | undefined = tracking.data;
  if (!t) return <Skeleton className="h-36" />;

  return (
    <Card className="overflow-hidden">
      <div className="flex items-end justify-between gap-4 p-5">
        <div>
          <p className="flex items-center gap-2 text-[13px] text-muted">
            <span className="relative flex size-2">
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-success opacity-60" />
              <span className="relative inline-flex size-2 rounded-full bg-success" />
            </span>
            Live · arriving in
          </p>
          <p className="mt-1 text-[44px] font-semibold leading-none tabular-nums">
            {t.etaMinutes ?? '—'}
            <span className="ml-1.5 text-lg font-medium text-muted">min</span>
          </p>
        </div>
        {t.estimatedDeliveryAt && (
          <p className="text-right text-sm text-muted">
            <Clock size={14} className="mb-0.5 mr-1 inline" />
            by {time(t.estimatedDeliveryAt)}
          </p>
        )}
      </div>
      <div className="flex items-center gap-3 border-t border-line bg-surface-2/50 px-5 py-4 text-sm">
        <span className="grid size-9 place-items-center rounded-sm bg-surface-3 text-muted"><Bike size={18} /></span>
        {t.partnerName ? (
          <div className="min-w-0 flex-1">
            <p className="font-medium">{t.partnerName}</p>
            <p className="text-[13px] text-muted">
              {t.partnerDistanceKm != null &&
                `${km(t.partnerDistanceKm)} ${t.status === 'PICKED_UP' ? 'from you' : 'from the restaurant'}`}
              {t.partnerLocationStale && ' · location may be out of date'}
            </p>
          </div>
        ) : (
          <p className="text-muted">Finding a delivery partner near the restaurant…</p>
        )}
      </div>
    </Card>
  );
}

function Timeline({ order }: { order: Order }) {
  const current = STEPS.findIndex((s) => s.status === order.status);
  const stamp: Partial<Record<OrderStatus, string | null>> = {
    PLACED: order.placedAt,
    ACCEPTED: order.acceptedAt,
    READY: order.readyAt,
    DELIVERED: order.deliveredAt,
  };

  return (
    <Card className="p-5">
      <h2 className="mb-5 text-base font-semibold">Progress</h2>
      <ol className="space-y-0">
        {STEPS.map((s, i) => {
          const done = i <= current;
          const Icon = s.icon;
          return (
            <li key={s.status} className="flex gap-4">
              <div className="flex flex-col items-center">
                <span
                  className={cn(
                    'grid size-8 place-items-center rounded-sm border transition-colors',
                    done ? 'border-fg bg-fg text-bg' : 'border-line text-subtle',
                    i === current && order.status !== 'DELIVERED' && 'ring-4 ring-fg/10',
                  )}
                >
                  <Icon size={15} />
                </span>
                {i < STEPS.length - 1 && <span className={cn('my-1 h-6 w-px', i < current ? 'bg-fg' : 'bg-line')} />}
              </div>
              <div className="pt-1.5">
                <p className={cn('text-sm font-medium', !done && 'text-subtle')}>{s.label}</p>
                {stamp[s.status] && <p className="text-xs text-muted">{time(stamp[s.status])}</p>}
              </div>
            </li>
          );
        })}
      </ol>
    </Card>
  );
}

function Bill({ order: o }: { order: Order }) {
  const payment = useQuery({
    queryKey: ['payment', o.id],
    queryFn: () => paymentApi.forOrder(o.id),
    retry: false, // 404 = not paid yet, that's fine
  });
  const p = payment.data;

  return (
    <Card className="p-5">
      <h2 className="text-base font-semibold">Bill</h2>
      <ul className="mt-4 space-y-2 text-sm">
        {o.items.map((i) => (
          <li key={i.id} className="flex justify-between gap-3">
            <span className="text-muted"><span className="text-fg">{i.quantity}×</span> {i.name}</span>
            <span className="tabular-nums">{money(i.itemTotal)}</span>
          </li>
        ))}
      </ul>
      <dl className="mt-4 space-y-2 border-t border-line pt-4 text-sm">
        <div className="flex justify-between"><dt className="text-muted">Subtotal</dt><dd className="tabular-nums">{money(o.subtotal)}</dd></div>
        <div className="flex justify-between"><dt className="text-muted">Delivery</dt><dd className="tabular-nums">{o.deliveryFee ? money(o.deliveryFee) : 'Free'}</dd></div>
        <div className="flex justify-between font-semibold"><dt>Total</dt><dd className="tabular-nums">{money(o.totalAmount)}</dd></div>
      </dl>
      <div className="mt-4 flex items-center justify-between border-t border-line pt-4 text-[13px]">
        <span className="text-muted">Payment</span>
        {p ? (
          <Badge tone={p.status === 'SUCCESS' ? 'success' : p.status === 'FAILED' ? 'danger' : p.status === 'REFUNDED' ? 'info' : 'warning'}>
            {p.method} · {p.status.toLowerCase()}
          </Badge>
        ) : (
          <Badge>{payment.isLoading ? '…' : 'Not paid'}</Badge>
        )}
      </div>
    </Card>
  );
}

function CancelOrder({ order }: { order: Order }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const cancel = useMutation({
    mutationFn: () => orderApi.cancel(order.id, reason.trim() || 'Changed my mind'),
    onSuccess: (o) => {
      qc.setQueryData(['order', order.id], o);
      qc.invalidateQueries({ queryKey: ['orders'] });
      qc.invalidateQueries({ queryKey: ['payment', order.id] });
      toast.success('Order cancelled.');
    },
    onError: toast.error,
  });

  if (!open) {
    return (
      <Button variant="ghost" className="w-full" onClick={() => setOpen(true)}>
        Cancel order
      </Button>
    );
  }
  return (
    <Card className="space-y-3 p-4">
      <Textarea label="Why are you cancelling?" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} placeholder="Ordered by mistake" />
      <div className="flex gap-2">
        <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>Cancel order</Button>
        <Button variant="ghost" onClick={() => setOpen(false)}>Keep it</Button>
      </div>
    </Card>
  );
}

function ReviewForm({ order }: { order: Order }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [food, setFood] = useState(0);
  const [delivery, setDelivery] = useState(0);
  const [comment, setComment] = useState('');
  const [done, setDone] = useState<null | 'new' | 'already'>(null);

  const submit = useMutation({
    mutationFn: () =>
      orderApi.review(order.id, {
        foodRating: food,
        deliveryRating: delivery || undefined,
        comment: comment.trim() || undefined,
      }),
    onSuccess: () => {
      setDone('new');
      qc.invalidateQueries({ queryKey: ['rating', order.restaurantId] });
      qc.invalidateQueries({ queryKey: ['reviews', order.restaurantId] });
      qc.invalidateQueries({ queryKey: ['restaurant', order.restaurantId] });
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 409) setDone('already');
      else toast.error(e);
    },
  });

  if (done) {
    return (
      <Card className="flex items-center gap-3 p-5 text-sm">
        <CircleCheck size={18} className="text-success" />
        {done === 'new' ? 'Thanks for your review!' : 'You already reviewed this order.'}
      </Card>
    );
  }

  return (
    <Card className="p-5">
      <h2 className="text-base font-semibold">How was it?</h2>
      <p className="mt-1 text-sm text-muted">Your review helps others choose. You can rate within 7 days of delivery.</p>
      <div className="mt-5 grid gap-5 sm:grid-cols-2">
        <StarInput label="Food" value={food} onChange={setFood} />
        <StarInput label="Delivery (optional)" value={delivery} onChange={setDelivery} />
      </div>
      <div className="mt-5">
        <Textarea label="Comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} placeholder="What did you like?" />
      </div>
      <Button className="mt-4" disabled={!food} loading={submit.isPending} onClick={() => submit.mutate()}>
        Submit review
      </Button>
    </Card>
  );
}
