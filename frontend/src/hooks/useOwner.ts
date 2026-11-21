import { useEffect, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ownerApi } from '@/api/endpoints';
import type { Order, UUID } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { useToast } from '@/components/Toast';
import { money } from '@/lib/format';
import { ding } from '@/lib/sound';

/*
 * Restaurant-owner data, shared by the dashboard, the nav badge and the
 * new-order alert. Same query key = one request, one cached copy.
 */

export const OWNER_KEYS = {
  restaurants: ['owner', 'restaurants'] as const,
  orders: (restaurantId: UUID) => ['owner', 'orders', restaurantId] as const,
  menu: (restaurantId: UUID) => ['owner', 'menu', restaurantId] as const,
};

/** The backend has no push (websocket) yet, so we poll. 10 s feels "live". */
const POLL_MS = 10_000;

export function useMyRestaurants() {
  const { user } = useAuth();
  return useQuery({
    queryKey: OWNER_KEYS.restaurants,
    queryFn: ownerApi.myRestaurants,
    enabled: user?.role === 'RESTAURANT_OWNER',
  });
}

/**
 * Orders of one or more restaurants, merged and newest first.
 * Each restaurant is its own query, so switching restaurants is instant
 * once loaded.
 */
export function useRestaurantOrders(restaurantIds: UUID[]) {
  const { user } = useAuth();
  const ids = [...restaurantIds].sort();
  return useQuery({
    queryKey: ['owner', 'orders', 'merged', ...ids],
    queryFn: async () => {
      const pages = await Promise.all(ids.map((id) => ownerApi.orders(id)));
      return pages
        .flatMap((p) => p.content)
        .sort((a, b) => (a.placedAt < b.placedAt ? 1 : -1));
    },
    enabled: user?.role === 'RESTAURANT_OWNER' && ids.length > 0,
    refetchInterval: POLL_MS,
    refetchIntervalInBackground: true, // keep checking while the tab is hidden → alert still fires
  });
}

/** New (PLACED) orders across ALL my restaurants — for the badge and the alert. */
export function useIncomingOrders() {
  const restaurants = useMyRestaurants();
  const orders = useRestaurantOrders((restaurants.data ?? []).map((r) => r.id));
  return { ...orders, data: orders.data?.filter((o) => o.status === 'PLACED') };
}

/** accept / preparing / ready / cancel, then refresh every owner list. */
export function useOrderActions() {
  const qc = useQueryClient();
  const toast = useToast();
  const done = (msg: string) => (o: Order) => {
    qc.setQueryData(['order', o.id], o);
    qc.invalidateQueries({ queryKey: ['owner', 'orders'] });
    toast.success(msg);
  };
  return {
    accept: useMutation({ mutationFn: ownerApi.accept, onSuccess: done('Order accepted.'), onError: toast.error }),
    preparing: useMutation({ mutationFn: ownerApi.preparing, onSuccess: done('Marked as preparing.'), onError: toast.error }),
    ready: useMutation({
      mutationFn: ownerApi.ready,
      onSuccess: done('Marked ready. Finding a rider…'),
      onError: toast.error,
    }),
  };
}

// ── New-order alert ──────────────────────────────────────────

/**
 * Mounted once (in AppShell) for owners. Watches incoming orders and, when a
 * NEW one appears: toast + ding + desktop notification + "(2) " in the tab title.
 */
export function useNewOrderAlerts() {
  const toast = useToast();
  const { data } = useIncomingOrders();
  const seen = useRef<Set<UUID> | null>(null);

  useEffect(() => {
    if (!data) return;
    if (seen.current === null) {
      // first load: don't ding for orders that were already waiting, just mention them
      seen.current = new Set(data.map((o) => o.id));
      if (data.length) toast.info(`${data.length} order${data.length > 1 ? 's' : ''} waiting for you to accept.`);
      return;
    }
    const fresh = data.filter((o) => !seen.current!.has(o.id));
    fresh.forEach((o) => seen.current!.add(o.id));
    if (!fresh.length) return;

    ding();
    for (const o of fresh) {
      const text = `${o.customerName} · ${o.items.reduce((n, i) => n + i.quantity, 0)} items · ${money(o.totalAmount)}`;
      toast.success(`New order at ${o.restaurantName}: ${text}`);
      if ('Notification' in window && Notification.permission === 'granted' && document.hidden) {
        new Notification(`New order · ${o.restaurantName}`, { body: text, tag: o.id });
      }
    }
  }, [data, toast]);

  // Tab title shows how many orders wait
  const count = data?.length ?? 0;
  useEffect(() => {
    const base = 'Food Delivery';
    document.title = count ? `(${count}) New order${count > 1 ? 's' : ''} · ${base}` : base;
    return () => {
      document.title = base;
    };
  }, [count]);
}
