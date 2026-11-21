import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bike, CircleCheck, Clock, Navigation, Package, Power, Star, Store, User, Wand2, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import { deliveryApi, orderApi } from '@/api/endpoints';
import type { DeliveryAssignment, PartnerStatus } from '@/api/types';
import { useConfirm } from '@/components/Confirm';
import { useToast } from '@/components/Toast';
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui';
import { Switch } from '@/components/Switch';
import { Animated } from '@/components/Animated';
import { cn, dateTime, money, relative } from '@/lib/format';
import { ding } from '@/lib/sound';

/*
 * /rider — the delivery partner's screen.
 *
 *  1. Go online → the backend can assign you READY orders near you.
 *  2. A job appears (ding!) → go to the restaurant → "Picked up".
 *  3. Go to the customer → "Delivered".
 *
 * The backend checks you are really there (geofence: within 0.5 km), using
 * the location this page sends. "Demo mode" moves your reported location to
 * the restaurant / customer for you, so you can test on one computer.
 */

type Coords = { lat: number; lng: number };
const DEFAULT_SPOT: Coords = { lat: 19.0596, lng: 72.8295 }; // Bandra, Mumbai (demo data area)
const DEMO_KEY = 'fd.rider.demo';
const HEARTBEAT_MS = 60_000; // backend treats a location older than 10 min as stale

const KEYS = {
  status: ['rider', 'status'] as const,
  current: ['rider', 'current'] as const,
  history: ['rider', 'history'] as const,
  rating: ['rider', 'rating'] as const,
};

function readDemo() {
  try {
    return localStorage.getItem(DEMO_KEY) !== 'off';
  } catch {
    return true;
  }
}

function gps(): Promise<Coords> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) return reject(new Error('This browser has no location support.'));
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => reject(new Error('Location permission denied.')),
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 30_000 },
    );
  });
}

const mapsLink = (lat: number, lng: number) => `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;

export function RiderDashboard() {
  const qc = useQueryClient();
  const toast = useToast();
  const [demo, setDemo] = useState(readDemo);
  const coords = useRef<Coords | null>(null);

  // 404 = never been online → treat as offline
  const status = useQuery({
    queryKey: KEYS.status,
    queryFn: async (): Promise<PartnerStatus | null> => {
      try {
        return await deliveryApi.status();
      } catch (e) {
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }
    },
  });
  const online = !!status.data?.isOnline;
  if (status.data?.latitude != null && status.data.longitude != null && !coords.current) {
    coords.current = { lat: status.data.latitude, lng: status.data.longitude };
  }

  const current = useQuery({
    queryKey: KEYS.current,
    queryFn: async () => (await deliveryApi.current()) ?? null, // 204 → null
    refetchInterval: online ? 8_000 : false,
    refetchIntervalInBackground: true,
  });

  const sendLocation = useCallback(
    async (c: Coords, available?: boolean) => {
      coords.current = c;
      const s = await deliveryApi.updateStatus(c.lat, c.lng, available);
      qc.setQueryData(KEYS.status, s);
      return s;
    },
    [qc],
  );

  /** Where am I? GPS in real mode; in demo mode the last known spot (or the demo area). */
  const whereAmI = useCallback(async (): Promise<Coords> => {
    if (!demo) return gps();
    return coords.current ?? DEFAULT_SPOT;
  }, [demo]);

  // Heartbeat: keep the location fresh while online (otherwise the backend stops assigning you)
  useEffect(() => {
    if (!online) return;
    const beat = async () => {
      try {
        await sendLocation(await whereAmI());
      } catch {
        /* next beat will retry */
      }
    };
    const t = setInterval(beat, HEARTBEAT_MS);
    return () => clearInterval(t);
  }, [online, whereAmI, sendLocation]);

  // Ding when a new job shows up
  const lastJob = useRef<string | null | undefined>(undefined);
  useEffect(() => {
    const id = current.data?.id ?? null;
    if (lastJob.current !== undefined && id && id !== lastJob.current) {
      ding();
      toast.success(`New delivery: pick up from ${current.data!.restaurantName}.`);
    }
    lastJob.current = id;
  }, [current.data, toast]);

  const toggle = useMutation({
    mutationFn: async () => sendLocation(await whereAmI(), !online),
    onSuccess: (s) => {
      toast.success(s.isOnline ? "You're online. Jobs near you will appear here." : "You're offline.");
      qc.invalidateQueries({ queryKey: KEYS.current });
    },
    onError: toast.error,
  });

  function setDemoMode(on: boolean) {
    setDemo(on);
    try {
      localStorage.setItem(DEMO_KEY, on ? 'on' : 'off');
    } catch {
      /* ignore */
    }
  }

  if (status.isLoading) return <Skeleton className="h-64" />;
  if (status.error) return <ErrorState error={status.error} onRetry={() => status.refetch()} />;

  return (
    <div>
      <PageHeader title="Deliveries" subtitle={online ? 'You are online' : 'Go online to start getting orders'} />

      <div className="grid gap-6 grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="space-y-6">
          {/* Online switch */}
          <Card className={cn('flex flex-wrap items-center gap-4 p-5', online && 'border-success/40 bg-success/5')}>
            <span className={cn('grid size-12 place-items-center rounded-md', online ? 'bg-success text-bg' : 'bg-surface-3 text-muted')}>
              <Power size={22} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-semibold">{online ? 'Online' : 'Offline'}</p>
              <p className="text-[13px] text-muted">
                {online
                  ? status.data?.isAvailable
                    ? 'Waiting for an order near you…'
                    : 'On a delivery'
                  : 'You will not receive any orders.'}
                {status.data?.lastSeenAt && <> · location sent {relative(status.data.lastSeenAt)}</>}
              </p>
            </div>
            <Button variant={online ? 'secondary' : 'primary'} loading={toggle.isPending} onClick={() => toggle.mutate()}>
              {online ? 'Go offline' : 'Go online'}
            </Button>
          </Card>

          {current.isLoading ? (
            <Skeleton className="h-64" />
          ) : current.data ? (
            <CurrentJob job={current.data} demo={demo} sendLocation={sendLocation} whereAmI={whereAmI} />
          ) : (
            <EmptyState
              icon={Bike}
              title={online ? 'No job right now' : "You're offline"}
              text={
                online
                  ? 'When a nearby restaurant marks an order ready, it is assigned to the closest free rider. This page checks every few seconds.'
                  : 'Go online to receive delivery jobs.'
              }
            />
          )}

          <History />
        </div>

        <aside className="space-y-4">
          <RatingCard />
          <Card className="p-5">
            <div className="flex items-start gap-3">
              <Wand2 size={18} className="mt-0.5 shrink-0 text-muted" />
              <div className="flex-1">
                <p className="text-sm font-medium">Demo mode</p>
                <p className="mt-1 text-xs leading-5 text-muted">
                  The backend only lets you pick up within 0.5 km of the restaurant and deliver within 0.5 km of the customer. Demo mode
                  "teleports" your location there, so you can test from your desk. Turn it off to use real GPS.
                </p>
              </div>
              <Switch checked={demo} onCheckedChange={setDemoMode} label="Demo mode" className="mt-0.5" />
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function CurrentJob({
  job,
  demo,
  sendLocation,
  whereAmI,
}: {
  job: DeliveryAssignment;
  demo: boolean;
  sendLocation: (c: { lat: number; lng: number }) => Promise<PartnerStatus>;
  whereAmI: () => Promise<{ lat: number; lng: number }>;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const confirm = useConfirm();
  // tracking gives the customer's coordinates (the assignment only has the text address)
  const tracking = useQuery({ queryKey: ['tracking', job.orderId], queryFn: () => orderApi.tracking(job.orderId) });
  const pickedUp = job.status === 'PICKED_UP';
  const drop = tracking.data?.deliveryLatitude != null ? { lat: tracking.data.deliveryLatitude, lng: tracking.data.deliveryLongitude! } : null;
  const pickup = job.restaurantLatitude != null ? { lat: job.restaurantLatitude, lng: job.restaurantLongitude! } : null;

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['rider'] });
    qc.invalidateQueries({ queryKey: ['tracking', job.orderId] });
  };

  /** Report "I'm here" (demo teleport or real GPS), then do the action. */
  async function atThenDo(target: { lat: number; lng: number } | null, action: () => Promise<unknown>) {
    await sendLocation(demo && target ? target : await whereAmI());
    return action();
  }

  const pick = useMutation({
    mutationFn: () => atThenDo(pickup, () => deliveryApi.pickup(job.orderId)),
    onSuccess: () => {
      toast.success('Picked up. Head to the customer.');
      refresh();
    },
    onError: toast.error,
  });
  const deliver = useMutation({
    mutationFn: () => atThenDo(drop, () => deliveryApi.deliver(job.orderId)),
    onSuccess: () => {
      toast.success('Delivered. Nice work!');
      refresh();
    },
    onError: toast.error,
  });
  const reject = useMutation({
    mutationFn: () => deliveryApi.reject(job.id),
    onSuccess: () => {
      toast.info('Job passed on to another rider.');
      refresh();
    },
    onError: toast.error,
  });

  return (
    <Card className="overflow-hidden border-info/40">
      <div className="flex items-center justify-between gap-3 border-b border-line bg-info/5 px-5 py-3">
        <p className="flex items-center gap-2 text-sm font-semibold">
          <span className="relative flex size-2">
            <span className="absolute inline-flex size-full animate-ping rounded-full bg-info opacity-60" />
            <span className="relative inline-flex size-2 rounded-full bg-info" />
          </span>
          {pickedUp ? 'Delivering' : 'Pick up'}
        </p>
        <span className="text-xs text-muted">
          <Clock size={12} className="mr-1 inline" />
          assigned {relative(job.assignedAt)}
        </span>
      </div>

      <ol className="space-y-0 p-5">
        <Stop
          icon={Store}
          done={pickedUp}
          title={job.restaurantName}
          subtitle={pickedUp ? `Picked up ${relative(job.pickedUpAt)}` : 'Collect the order here'}
          nav={pickup}
        />
        <Stop
          icon={User}
          last
          done={false}
          title={job.customerName}
          subtitle={job.deliveryAddressText}
          nav={drop}
        />
      </ol>

      <div className="flex items-center justify-between border-t border-line px-5 py-3 text-sm">
        <Link to={`/orders/${job.orderId}`} className="text-muted hover:text-fg">
          Order #{job.orderId.slice(0, 6).toUpperCase()}
        </Link>
        <span>
          <span className="text-muted">Order value: </span>
          <span className="font-semibold tabular-nums">{money(job.orderTotal)}</span>
        </span>
      </div>

      <div className="flex gap-2 border-t border-line bg-surface-2/40 p-4">
        {!pickedUp ? (
          <>
            <Button className="flex-1" size="lg" icon={Package} loading={pick.isPending} disabled={job.orderStatus !== 'READY'} onClick={() => pick.mutate()}>
              {job.orderStatus === 'READY' ? 'Picked up' : 'Waiting for kitchen…'}
            </Button>
            <Button variant="ghost" size="lg" icon={X} loading={reject.isPending} onClick={async () => (await confirm({ title: 'Pass this job on?', text: 'It goes to the next closest rider. You may not get a new job for a few minutes.', confirmLabel: 'Pass it on', danger: true })) && reject.mutate()}>
              Reject
            </Button>
          </>
        ) : (
          <Button className="flex-1" size="lg" icon={CircleCheck} loading={deliver.isPending} onClick={() => deliver.mutate()}>
            Delivered
          </Button>
        )}
      </div>
      {demo && <p className="px-5 pb-4 text-xs text-subtle">Demo mode is on: your location is moved to the stop when you tap the button.</p>}
    </Card>
  );
}

function Stop({
  icon: Icon,
  title,
  subtitle,
  done,
  last,
  nav,
}: {
  icon: typeof Store;
  title: string;
  subtitle: string;
  done: boolean;
  last?: boolean;
  nav: { lat: number; lng: number } | null;
}) {
  return (
    <li className="flex gap-4">
      <div className="flex flex-col items-center">
        <span className={cn('grid size-9 place-items-center rounded-sm border', done ? 'border-fg bg-fg text-bg' : 'border-line text-muted')}>
          <Icon size={16} />
        </span>
        {!last && <span className={cn('my-1 h-8 w-px', done ? 'bg-fg' : 'bg-line')} />}
      </div>
      <div className="min-w-0 flex-1 pt-1">
        <p className="font-medium">{title}</p>
        <p className="text-[13px] text-muted">{subtitle}</p>
      </div>
      {nav && (
        <a href={mapsLink(nav.lat, nav.lng)} target="_blank" rel="noreferrer" className="self-start">
          <Button variant="secondary" size="sm" icon={Navigation}>Navigate</Button>
        </a>
      )}
    </li>
  );
}

function RatingCard() {
  const rating = useQuery({ queryKey: KEYS.rating, queryFn: deliveryApi.rating });
  const history = useQuery({ queryKey: KEYS.history, queryFn: () => deliveryApi.history() });
  const delivered = (history.data?.content ?? []).filter((a) => a.status === 'DELIVERED');
  const today = new Date().toDateString();
  const todayCount = delivered.filter((a) => a.deliveredAt && new Date(a.deliveredAt).toDateString() === today).length;

  return (
    <Card className="grid grid-cols-2 divide-x divide-line">
      <div className="p-5">
        <p className="flex items-center gap-1.5 text-xs text-muted"><Star size={13} /> Rating</p>
        <p className="mt-2 text-2xl font-semibold tabular-nums">
          {rating.data?.avgDeliveryRating != null ? rating.data.avgDeliveryRating.toFixed(1) : '—'}
        </p>
        <p className="text-xs text-subtle">{rating.data?.totalRatings ?? 0} ratings</p>
      </div>
      <div className="p-5">
        <p className="flex items-center gap-1.5 text-xs text-muted"><CircleCheck size={13} /> Today</p>
        <p className="mt-2 text-2xl font-semibold tabular-nums">{todayCount}</p>
        <p className="text-xs text-subtle">deliveries</p>
      </div>
    </Card>
  );
}

function History() {
  const history = useQuery({ queryKey: KEYS.history, queryFn: () => deliveryApi.history() });
  const rows = (history.data?.content ?? []).filter((a) => a.status === 'DELIVERED' || a.status === 'CANCELLED');
  if (!rows.length) return null;
  return (
    <section>
      <h2 className="mb-3 text-base font-semibold">Past deliveries</h2>
      <Animated as="ul" className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface">
        {rows.map((a) => (
          <li key={a.id} className="flex items-center gap-4 px-4 py-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{a.restaurantName} → {a.customerName}</p>
              <p className="text-xs text-muted">{dateTime(a.deliveredAt ?? a.assignedAt)}</p>
            </div>
            <Badge tone={a.status === 'DELIVERED' ? 'success' : 'neutral'}>{a.status === 'DELIVERED' ? 'Delivered' : 'Passed on'}</Badge>
            <span className="w-16 text-right tabular-nums">{money(a.orderTotal)}</span>
          </li>
        ))}
      </Animated>
    </section>
  );
}
