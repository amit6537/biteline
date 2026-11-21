import { useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { LogOut, MapPin, MonitorSmartphone, Trash2 } from 'lucide-react';
import { addressApi } from '@/api/endpoints';
import { useAuth } from '@/auth/AuthContext';
import { useConfirm } from '@/components/Confirm';
import { useToast } from '@/components/Toast';
import { Badge, Button, Card, PageHeader, Skeleton } from '@/components/ui';
import { initials } from '@/lib/format';
import { ROLE_LABEL } from '@/lib/roles';

export function AccountPage() {
  const { user, logout, logoutAll } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const isCustomer = user?.role === 'CUSTOMER';
  const addresses = useQuery({ queryKey: ['addresses'], queryFn: addressApi.list, enabled: isCustomer });
  const remove = useMutation({
    mutationFn: addressApi.remove,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
    onError: toast.error,
  });
  const everywhere = useMutation({
    mutationFn: logoutAll,
    onSuccess: () => {
      toast.success('Logged out on all devices.');
      navigate('/login');
    },
    onError: toast.error,
  });

  if (!user) return null;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader title="Account" />

      <Card className="flex items-center gap-4 p-5">
        <span className="grid size-14 place-items-center rounded-md bg-surface-3 text-lg font-semibold">{initials(user.name)}</span>
        <div className="min-w-0">
          <p className="truncate text-base font-semibold">{user.name}</p>
          <p className="truncate text-sm text-muted">{user.email}</p>
        </div>
        <Badge className="ml-auto">{ROLE_LABEL[user.role]}</Badge>
      </Card>

      {isCustomer && (
        <section>
          <h2 className="mb-3 text-base font-semibold">Saved addresses</h2>
          {addresses.isLoading ? (
            <Skeleton className="h-20" />
          ) : !addresses.data?.length ? (
            <p className="text-sm text-muted">No saved addresses yet. You can add one at checkout.</p>
          ) : (
            <Card className="divide-y divide-line">
              {addresses.data.map((a) => (
                <div key={a.id} className="flex items-start gap-3 p-4">
                  <MapPin size={17} className="mt-0.5 shrink-0 text-muted" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">
                      {a.label} {a.isDefault && <Badge className="ml-1">Default</Badge>}
                    </p>
                    <p className="text-[13px] text-muted">{a.formattedAddress}</p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={Trash2}
                    aria-label={`Delete ${a.label}`}
                    loading={remove.isPending && remove.variables === a.id}
                    onClick={async () => (await confirm({ title: `Delete "${a.label}"?`, text: a.formattedAddress, confirmLabel: 'Delete', danger: true })) && remove.mutate(a.id)}
                  />
                </div>
              ))}
            </Card>
          )}
        </section>
      )}

      <section>
        <h2 className="mb-3 text-base font-semibold">Sessions</h2>
        <Card className="divide-y divide-line">
          <div className="flex items-center gap-4 p-4">
            <LogOut size={18} className="text-muted" />
            <p className="flex-1 text-sm">Log out of this device</p>
            <Button variant="secondary" size="sm" onClick={async () => { await logout(); navigate('/'); }}>Log out</Button>
          </div>
          <div className="flex items-center gap-4 p-4">
            <MonitorSmartphone size={18} className="text-muted" />
            <div className="flex-1">
              <p className="text-sm">Log out everywhere</p>
              <p className="text-xs text-muted">Lost a phone? This ends every session on every device.</p>
            </div>
            <Button variant="danger" size="sm" loading={everywhere.isPending} onClick={async () => (await confirm({ title: 'Log out everywhere?', text: 'Every phone and browser signed in to this account will be logged out, including this one.', confirmLabel: 'Log out all', danger: true })) && everywhere.mutate()}>
              Log out all
            </Button>
          </div>
        </Card>
      </section>
    </div>
  );
}
