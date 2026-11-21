import { useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck } from 'lucide-react';
import { notificationApi } from '@/api/endpoints';
import type { AppNotification } from '@/api/types';
import { useToast } from '@/components/Toast';
import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui';
import { Animated } from '@/components/Animated';
import { cn, relative } from '@/lib/format';

export function NotificationsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: () => notificationApi.list(), refetchInterval: 30_000 });

  const refresh = () => qc.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({ mutationFn: notificationApi.markRead, onSuccess: refresh });
  const markAll = useMutation({ mutationFn: notificationApi.markAllRead, onSuccess: refresh, onError: toast.error });

  const items = list.data?.content ?? [];
  const unread = items.filter((n) => !n.isRead).length;

  function open(n: AppNotification) {
    if (!n.isRead) markRead.mutate(n.id);
    if (n.orderId) navigate(`/orders/${n.orderId}`);
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        title="Notifications"
        subtitle={unread ? `${unread} unread` : undefined}
        action={
          unread > 0 && (
            <Button variant="secondary" size="sm" icon={CheckCheck} loading={markAll.isPending} onClick={() => markAll.mutate()}>
              Mark all read
            </Button>
          )
        }
      />
      {list.isLoading ? (
        <div className="space-y-2">{Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-16" />)}</div>
      ) : list.error ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState icon={Bell} title="You're all caught up" text="Order updates will appear here." />
      ) : (
        <Animated as="ul" className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface">
          {items.map((n) => (
            <li key={n.id}>
              <button onClick={() => open(n)} className="flex w-full gap-3 px-5 py-4 text-left transition-colors hover:bg-surface-2">
                <span className={cn('mt-1.5 size-2 shrink-0 rounded-full', n.isRead ? 'bg-transparent' : 'bg-fg')} />
                <span className="min-w-0 flex-1">
                  <span className={cn('block text-sm', n.isRead ? 'text-muted' : 'font-semibold')}>{n.title}</span>
                  <span className="mt-0.5 block text-[13px] leading-5 text-muted">{n.message}</span>
                  <span className="mt-1 block text-xs text-subtle">{relative(n.createdAt)}</span>
                </span>
              </button>
            </li>
          ))}
        </Animated>
      )}
    </div>
  );
}
