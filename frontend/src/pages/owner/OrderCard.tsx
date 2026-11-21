import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Bike, Check, ChefHat, Clock, MapPin, MessageSquareText, Package, X } from 'lucide-react';
import { orderApi } from '@/api/endpoints';
import type { Order } from '@/api/types';
import { useToast } from '@/components/Toast';
import { Badge, Button, Card, StatusBadge, Textarea } from '@/components/ui';
import { useOrderActions } from '@/hooks/useOwner';
import { cn, money, relative, time } from '@/lib/format';

/** One order on the kitchen board, with the next action button. */
export function OrderCard({ order: o, showRestaurant }: { order: Order; showRestaurant?: boolean }) {
  const isNew = o.status === 'PLACED';
  return (
    <Card className={cn('overflow-hidden', isNew && 'border-info/50 ring-1 ring-info/25')}>
      <div className="flex items-start justify-between gap-3 px-4 pt-4">
        <div className="min-w-0">
          <Link to={`/orders/${o.id}`} viewTransition className="text-[15px] font-semibold hover:underline">
            #{o.id.slice(0, 6).toUpperCase()} · {o.customerName}
          </Link>
          <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted">
            <Clock size={12} /> {time(o.placedAt)} · {relative(o.placedAt)}
            {showRestaurant && <> · {o.restaurantName}</>}
          </p>
        </div>
        {isNew ? <Badge tone="info">New</Badge> : <StatusBadge status={o.status} />}
      </div>

      <ul className="space-y-1 px-4 pt-3 text-sm">
        {o.items.map((i) => (
          <li key={i.id} className="flex justify-between gap-3">
            <span>
              <span className="font-semibold tabular-nums">{i.quantity}×</span> <span className="text-muted">{i.name}</span>
            </span>
          </li>
        ))}
      </ul>

      {o.specialInstructions && (
        <p className="mx-4 mt-3 flex gap-2 rounded-sm bg-warning/10 px-3 py-2 text-[13px] text-warning">
          <MessageSquareText size={14} className="mt-0.5 shrink-0" /> {o.specialInstructions}
        </p>
      )}

      <div className="mt-3 flex items-center justify-between gap-3 px-4 text-[13px] text-muted">
        <span className="flex min-w-0 items-center gap-1.5">
          <MapPin size={13} className="shrink-0" /> <span className="truncate">{o.deliveryAddressText}</span>
        </span>
        <span className="shrink-0 font-semibold text-fg tabular-nums">{money(o.totalAmount)}</span>
      </div>

      <div className="mt-4 border-t border-line bg-surface-2/40 p-3">
        <OrderActions order={o} />
      </div>
    </Card>
  );
}

/** The buttons for the order's current step. Also used on the order detail page. */
export function OrderActions({ order: o }: { order: Order }) {
  const { accept, preparing, ready } = useOrderActions();
  const [rejecting, setRejecting] = useState(false);

  if (rejecting) return <RejectForm order={o} onClose={() => setRejecting(false)} />;

  switch (o.status) {
    case 'PLACED':
      return (
        <div className="flex gap-2">
          <Button className="flex-1" icon={Check} loading={accept.isPending} onClick={() => accept.mutate(o.id)}>
            Accept order
          </Button>
          <Button variant="ghost" icon={X} onClick={() => setRejecting(true)}>
            Reject
          </Button>
        </div>
      );
    case 'ACCEPTED':
      return (
        <div className="flex gap-2">
          <Button className="flex-1" icon={ChefHat} loading={preparing.isPending} onClick={() => preparing.mutate(o.id)}>
            Start preparing
          </Button>
          <Button variant="ghost" onClick={() => setRejecting(true)}>
            Cancel
          </Button>
        </div>
      );
    case 'PREPARING':
      return (
        <div className="flex gap-2">
          <Button className="flex-1" icon={Package} loading={ready.isPending} onClick={() => ready.mutate(o.id)}>
            Mark ready for pickup
          </Button>
          <Button variant="ghost" onClick={() => setRejecting(true)}>
            Cancel
          </Button>
        </div>
      );
    case 'READY':
      return (
        <p className="flex items-center gap-2 px-1 text-sm text-muted">
          <Bike size={16} /> Packed. Waiting for the rider to pick it up.
        </p>
      );
    case 'PICKED_UP':
      return (
        <p className="flex items-center gap-2 px-1 text-sm text-muted">
          <Bike size={16} /> Out for delivery
        </p>
      );
    case 'DELIVERED':
      return <p className="px-1 text-sm text-success">Delivered {relative(o.deliveredAt)}</p>;
    case 'CANCELLED':
      return <p className="px-1 text-sm text-danger">Cancelled · {o.cancellationReason ?? 'no reason'}</p>;
  }
}

const REASONS = ['Kitchen is too busy', 'Item out of stock', 'Closing soon'];

function RejectForm({ order, onClose }: { order: Order; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [reason, setReason] = useState('');
  const cancel = useMutation({
    mutationFn: () => orderApi.cancel(order.id, reason.trim() || REASONS[0]!),
    onSuccess: (o) => {
      qc.setQueryData(['order', o.id], o);
      qc.invalidateQueries({ queryKey: ['owner', 'orders'] });
      toast.success('Order cancelled. The customer has been notified (and refunded if paid).');
      onClose();
    },
    onError: toast.error,
  });

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5">
        {REASONS.map((r) => (
          <button
            key={r}
            type="button"
            onClick={() => setReason(r)}
            className={cn(
              'rounded-sm border px-2.5 py-1 text-xs transition-colors',
              reason === r ? 'border-fg bg-surface-3 text-fg' : 'border-line text-muted hover:text-fg',
            )}
          >
            {r}
          </button>
        ))}
      </div>
      <Textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Reason (the customer sees this)" maxLength={200} className="min-h-14" />
      <div className="flex gap-2">
        <Button variant="danger" size="sm" loading={cancel.isPending} onClick={() => cancel.mutate()}>
          Cancel order
        </Button>
        <Button variant="ghost" size="sm" onClick={onClose}>
          Keep it
        </Button>
      </div>
    </div>
  );
}
