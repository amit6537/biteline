import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { BellRing, ChefHat, CircleCheck, IndianRupee, Inbox, Package, Plus, Settings2, Store } from 'lucide-react';
import { ownerApi } from '@/api/endpoints';
import type { Order, OrderStatus, Restaurant } from '@/api/types';
import { useToast } from '@/components/Toast';
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, Rating, Skeleton, Thumb } from '@/components/ui';
import { Switch } from '@/components/Switch';
import { Animated } from '@/components/Animated';
import { OWNER_KEYS, useMyRestaurants, useRestaurantOrders } from '@/hooks/useOwner';
import { cn, money, toDate } from '@/lib/format';
import { OrderCard } from './OrderCard';

/*
 * /owner — the kitchen screen.
 *   - pick a restaurant (or "All")
 *   - today's numbers
 *   - a board: New → In the kitchen → Ready / on the way, refreshed every 10 s
 *   - your restaurants with an open/closed switch
 */

const COLUMNS: { key: string; title: string; icon: typeof Inbox; statuses: OrderStatus[]; empty: string }[] = [
  { key: 'new', title: 'New', icon: Inbox, statuses: ['PLACED'], empty: 'New orders appear here (with a ding).' },
  { key: 'kitchen', title: 'In the kitchen', icon: ChefHat, statuses: ['ACCEPTED', 'PREPARING'], empty: 'Nothing cooking.' },
  { key: 'out', title: 'Ready · on the way', icon: Package, statuses: ['READY', 'PICKED_UP'], empty: 'Nothing waiting for a rider.' },
];

export function OwnerDashboard() {
  const restaurants = useMyRestaurants();
  const [params, setParams] = useSearchParams();
  const selected = params.get('r') ?? 'all';
  const list = restaurants.data ?? [];
  const ids = selected === 'all' ? list.map((r) => r.id) : [selected];
  const orders = useRestaurantOrders(ids);

  if (restaurants.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-1/3" />
        <Skeleton className="h-24" />
        <Skeleton className="h-80" />
      </div>
    );
  }
  if (restaurants.error) return <ErrorState error={restaurants.error} onRetry={() => restaurants.refetch()} />;

  if (!list.length) {
    return (
      <EmptyState
        icon={Store}
        title="Add your first restaurant"
        text="Set up your kitchen and menu. Customers can order as soon as it's open."
        action={<Link to="/owner/restaurants/new"><Button icon={Plus}>Add restaurant</Button></Link>}
      />
    );
  }

  const all = orders.data ?? [];
  const today = new Date().toDateString();
  const todays = all.filter((o) => toDate(o.placedAt)?.toDateString() === today);
  const delivered = todays.filter((o) => o.status === 'DELIVERED');
  const past = all.filter((o) => o.status === 'DELIVERED' || o.status === 'CANCELLED').slice(0, 12);

  return (
    <div>
      <PageHeader
        title="Kitchen"
        subtitle={
          <span className="inline-flex items-center gap-2">
            <span className="relative flex size-2">
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-success opacity-60" />
              <span className="relative inline-flex size-2 rounded-full bg-success" />
            </span>
            Live · checks for new orders every 10 seconds
          </span>
        }
        action={<NotifyButton />}
      />

      {/* Restaurant switcher */}
      <div className="no-scrollbar -mx-4 mb-6 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:px-0">
        {list.length > 1 && (
          <Chip active={selected === 'all'} onClick={() => setParams({})}>All restaurants</Chip>
        )}
        {list.map((r) => (
          <Chip key={r.id} active={selected === r.id || list.length === 1} onClick={() => setParams({ r: r.id })}>
            <span className={cn('size-1.5 rounded-full', r.isOpen ? 'bg-success' : 'bg-subtle')} />
            {r.name}
          </Chip>
        ))}
        <Link to="/owner/restaurants/new" className="shrink-0">
          <Button variant="ghost" size="sm" icon={Plus}>Add</Button>
        </Link>
      </div>

      {/* Today */}
      <div className="mb-8 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat icon={BellRing} label="Waiting to accept" value={all.filter((o) => o.status === 'PLACED').length} highlight />
        <Stat icon={ChefHat} label="In the kitchen" value={all.filter((o) => o.status === 'ACCEPTED' || o.status === 'PREPARING').length} />
        <Stat icon={CircleCheck} label="Delivered today" value={delivered.length} />
        <Stat icon={IndianRupee} label="Sales today" value={money(delivered.reduce((s, o) => s + o.totalAmount, 0))} />
      </div>

      {/* Board */}
      {orders.isLoading ? (
        <div className="grid gap-4 lg:grid-cols-3">{COLUMNS.map((c) => <Skeleton key={c.key} className="h-72" />)}</div>
      ) : orders.error ? (
        <ErrorState error={orders.error} onRetry={() => orders.refetch()} />
      ) : (
        <div className="grid items-start gap-4 lg:grid-cols-3">
          {COLUMNS.map((col) => {
            const rows = all.filter((o) => col.statuses.includes(o.status)).reverse(); // oldest first = cook in order
            const Icon = col.icon;
            return (
              <section key={col.key} className="rounded-lg border border-line bg-surface/50 p-3">
                <h2 className="mb-3 flex items-center gap-2 px-1 text-sm font-semibold">
                  <Icon size={16} className="text-muted" /> {col.title}
                  <span className="ml-auto rounded-sm bg-surface-3 px-2 py-0.5 text-xs tabular-nums text-muted">{rows.length}</span>
                </h2>
                {rows.length ? (
                  <Animated className="space-y-3">
                    {rows.map((o) => <OrderCard key={o.id} order={o} showRestaurant={selected === 'all' && list.length > 1} />)}
                  </Animated>
                ) : (
                  <p className="rounded-md border border-dashed border-line px-4 py-8 text-center text-[13px] text-subtle">{col.empty}</p>
                )}
              </section>
            );
          })}
        </div>
      )}

      {past.length > 0 && <PastOrders orders={past} />}

      <section className="mt-12">
        <h2 className="mb-4 text-lg font-semibold">Your restaurants</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((r) => <RestaurantTile key={r.id} r={r} />)}
        </div>
      </section>
    </div>
  );
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'inline-flex h-9 shrink-0 items-center gap-2 rounded-sm border px-3 text-sm transition-colors',
        active ? 'border-fg bg-fg font-medium text-bg' : 'border-line bg-surface text-muted hover:text-fg',
      )}
    >
      {children}
    </button>
  );
}

function Stat({ icon: Icon, label, value, highlight }: { icon: typeof Inbox; label: string; value: number | string; highlight?: boolean }) {
  const hot = highlight && typeof value === 'number' && value > 0;
  return (
    <Card className={cn('p-4', hot && 'border-info/50 bg-info/5')}>
      <p className="flex items-center gap-1.5 text-xs text-muted"><Icon size={13} /> {label}</p>
      <p className={cn('mt-2 text-2xl font-semibold tabular-nums', hot && 'text-info')}>{value}</p>
    </Card>
  );
}

function PastOrders({ orders }: { orders: Order[] }) {
  return (
    <section className="mt-10">
      <h2 className="mb-3 text-base font-semibold">Recent history</h2>
      <Animated as="ul" className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface">
        {orders.map((o) => (
          <li key={o.id}>
            <Link to={`/orders/${o.id}`} className="flex items-center gap-4 px-4 py-3 text-sm hover:bg-surface-2">
              <span className="w-20 shrink-0 font-medium">#{o.id.slice(0, 6).toUpperCase()}</span>
              <span className="min-w-0 flex-1 truncate text-muted">
                {o.customerName} · {o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}
              </span>
              <Badge tone={o.status === 'DELIVERED' ? 'success' : 'danger'}>{o.status === 'DELIVERED' ? 'Delivered' : 'Cancelled'}</Badge>
              <span className="w-16 text-right tabular-nums">{money(o.totalAmount)}</span>
            </Link>
          </li>
        ))}
      </Animated>
    </section>
  );
}

function RestaurantTile({ r }: { r: Restaurant }) {
  const qc = useQueryClient();
  const toast = useToast();
  const toggle = useMutation({
    mutationFn: () => ownerApi.update(r.id, { isOpen: !r.isOpen }),
    onSuccess: (updated) => {
      qc.setQueryData<Restaurant[]>(OWNER_KEYS.restaurants, (old) => old?.map((x) => (x.id === updated.id ? updated : x)));
      qc.invalidateQueries({ queryKey: ['restaurant', r.id] });
      toast.success(updated.isOpen ? `${r.name} is open for orders.` : `${r.name} is closed. No new orders will come in.`);
    },
    onError: toast.error,
  });

  return (
    <Card className="overflow-hidden">
      <Thumb src={r.imageUrl} name={r.name} hint={r.cuisineType} width={600} className="aspect-[16/8] w-full" />
      <div className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h3 className="truncate font-semibold">{r.name}</h3>
            <p className="truncate text-[13px] text-muted">{r.cuisineType || r.city}</p>
          </div>
          <Rating value={r.avgRating} count={r.totalReviews} />
        </div>
        <div className="mt-4 flex items-center gap-2">
          <label className="flex items-center gap-2.5 text-sm font-medium">
            <Switch checked={r.isOpen} onCheckedChange={() => toggle.mutate()} disabled={toggle.isPending} label={`${r.name} open for orders`} />
            <span className={r.isOpen ? 'text-fg' : 'text-muted'}>{r.isOpen ? 'Open' : 'Closed'}</span>
          </label>
          <Link to={`/owner/restaurants/${r.id}`} className="ml-auto">
            <Button variant="secondary" size="sm" icon={Settings2}>Menu & settings</Button>
          </Link>
        </div>
      </div>
    </Card>
  );
}

/** Ask once for desktop notifications, so the owner hears about orders while on another tab. */
function NotifyButton() {
  const supported = typeof window !== 'undefined' && 'Notification' in window;
  const [perm, setPerm] = useState(supported ? Notification.permission : 'denied');
  useEffect(() => {
    if (supported) setPerm(Notification.permission);
  }, [supported]);
  if (!supported || perm !== 'default') return null;
  return (
    <Button variant="secondary" size="sm" icon={BellRing} onClick={async () => setPerm(await Notification.requestPermission())}>
      Enable desktop alerts
    </Button>
  );
}
