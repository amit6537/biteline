import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Banknote, Check, CreditCard, LocateFixed, MapPin, Plus, ShoppingBag, Smartphone, Trash2, Wallet } from 'lucide-react';
import { ApiError } from '@/api/client';
import { addressApi, orderApi, paymentApi } from '@/api/endpoints';
import type { Address, CreateAddressRequest, PaymentMethod } from '@/api/types';
import { useConfirm } from '@/components/Confirm';
import { useToast } from '@/components/Toast';
import { Button, Card, EmptyState, ErrorState, Input, PageHeader, QtyStepper, Skeleton, Textarea, Thumb } from '@/components/ui';
import { Animated } from '@/components/Animated';
import { CART_KEY, useCart, useCartActions } from '@/hooks/useCart';
import { cn, money } from '@/lib/format';

const METHODS: { value: PaymentMethod; label: string; icon: typeof Wallet }[] = [
  { value: 'UPI', label: 'UPI', icon: Smartphone },
  { value: 'CARD', label: 'Card', icon: CreditCard },
  { value: 'WALLET', label: 'Wallet', icon: Wallet },
  { value: 'CASH', label: 'Cash on delivery', icon: Banknote },
];

export function CartPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const cart = useCart();
  const { setQty, clear } = useCartActions();
  const addresses = useQuery({ queryKey: ['addresses'], queryFn: addressApi.list });

  const [addressId, setAddressId] = useState<string | null>(null);
  const [notes, setNotes] = useState('');
  const [method, setMethod] = useState<PaymentMethod>('UPI');
  const [placing, setPlacing] = useState(false);
  // One key per checkout attempt: pressing "Place order" twice never charges twice
  const payKey = useRef<string>(crypto.randomUUID());

  // Pick the default (or first) address once they load
  useEffect(() => {
    const list = addresses.data;
    if (!addressId && list?.length) setAddressId((list.find((a) => a.isDefault) ?? list[0]!).id);
  }, [addresses.data, addressId]);

  if (cart.isLoading) {
    return (
      <div className="grid gap-8 grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_380px]">
        <Skeleton className="h-80" />
        <Skeleton className="h-80" />
      </div>
    );
  }
  if (cart.error) return <ErrorState error={cart.error} onRetry={() => cart.refetch()} />;

  const c = cart.data;
  if (!c || c.items.length === 0) {
    return (
      <EmptyState
        icon={ShoppingBag}
        title="Your cart is empty"
        text="Add dishes from a restaurant to get started."
        action={<Link to="/"><Button>Browse restaurants</Button></Link>}
      />
    );
  }

  const unavailable = c.items.some((i) => !i.isAvailable);

  async function placeOrder() {
    if (!addressId) {
      toast.info('Add a delivery address first.');
      return;
    }
    setPlacing(true);
    try {
      const order = await orderApi.place(addressId, notes.trim() || undefined);
      qc.setQueryData(CART_KEY, undefined);
      qc.invalidateQueries({ queryKey: CART_KEY }); // the backend empties the cart
      qc.invalidateQueries({ queryKey: ['orders'] });

      try {
        await paymentApi.pay(order.id, method, payKey.current);
      } catch (e) {
        toast.error(`Order placed, but payment failed: ${e instanceof Error ? e.message : ''}`);
      }
      payKey.current = crypto.randomUUID();
      toast.success('Order placed!');
      navigate(`/orders/${order.id}`, { replace: true });
    } catch (e) {
      toast.error(e);
    } finally {
      setPlacing(false);
    }
  }

  return (
    <div>
      <PageHeader title="Checkout" subtitle={<>From <Link to={`/restaurants/${c.restaurantId}`} className="text-fg hover:underline">{c.restaurantName}</Link></>} />

      <div className="grid gap-8 grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_380px]">
        <div className="space-y-8">
          <Section
            title="Items"
            action={
              <Button variant="ghost" size="sm" icon={Trash2} loading={clear.isPending} onClick={async () => (await confirm({ title: 'Clear your cart?', text: 'All dishes will be removed.', confirmLabel: 'Clear cart', danger: true })) && clear.mutate(undefined, { onError: toast.error })}>
                Clear
              </Button>
            }
          >
            <Animated className="divide-y divide-line rounded-md border border-line bg-surface">
              {c.items.map((item) => (
                <div key={item.id} className={cn('flex items-center gap-4 p-4', !item.isAvailable && 'opacity-55')}>
                  <Thumb src={item.imageUrl} name={item.menuItemName} width={200} className="size-14 shrink-0 rounded-sm text-base" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{item.menuItemName}</p>
                    <p className="text-[13px] text-muted">
                      {item.isAvailable ? money(item.price) : 'No longer available, please remove'}
                    </p>
                  </div>
                  <QtyStepper
                    value={item.quantity}
                    busy={setQty.isPending && setQty.variables?.itemId === item.id}
                    onChange={(q) => setQty.mutate({ itemId: item.id, quantity: q }, { onError: toast.error })}
                  />
                  <p className="w-16 text-right text-sm font-medium tabular-nums">{money(item.itemTotal)}</p>
                </div>
              ))}
            </Animated>
          </Section>

          <Section title="Deliver to">
            {addresses.isLoading ? (
              <Skeleton className="h-24" />
            ) : (
              <AddressPicker list={addresses.data ?? []} selected={addressId} onSelect={setAddressId} />
            )}
          </Section>

          <Section title="Notes for the restaurant">
            <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} placeholder="Less spicy, no onions, ring the bell…" />
          </Section>

          <Section title="Payment">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {METHODS.map(({ value, label, icon: Icon }) => (
                <button
                  key={value}
                  onClick={() => setMethod(value)}
                  aria-pressed={method === value}
                  className={cn(
                    'flex h-20 flex-col items-start justify-between rounded-md border p-3 text-left text-[13px] font-medium transition-colors',
                    method === value ? 'border-fg bg-surface-2' : 'border-line bg-surface text-muted hover:border-line-strong',
                  )}
                >
                  <Icon size={18} />
                  {label}
                </button>
              ))}
            </div>
            <p className="mt-2 text-xs text-subtle">Demo payments: nothing is actually charged.</p>
          </Section>
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <Card className="p-5">
            <h2 className="text-base font-semibold">Bill details</h2>
            <dl className="mt-4 space-y-2.5 text-sm">
              <Row label={`Items (${c.totalItemCount})`} value={money(c.subtotal)} />
              <Row label="Delivery fee" value={c.deliveryFee ? money(c.deliveryFee) : 'Free'} />
              <div className="border-t border-line pt-3">
                <Row label={<span className="font-semibold text-fg">To pay</span>} value={<span className="text-base font-semibold text-fg">{money(c.totalAmount)}</span>} />
              </div>
            </dl>
            <Button size="lg" className="mt-5 w-full" loading={placing} disabled={unavailable || !addressId} onClick={placeOrder}>
              Place order · {money(c.totalAmount)}
            </Button>
            {unavailable && <p className="mt-2 text-xs text-danger">Remove unavailable items to continue.</p>}
            {!addressId && !addresses.isLoading && <p className="mt-2 text-xs text-muted">Add a delivery address to continue.</p>}
          </Card>
        </aside>
      </div>
    </div>
  );
}

function Section({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-base font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function Row({ label, value }: { label: React.ReactNode; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-muted">{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  );
}

function AddressPicker({ list, selected, onSelect }: { list: Address[]; selected: string | null; onSelect: (id: string) => void }) {
  const [adding, setAdding] = useState(list.length === 0);
  useEffect(() => {
    if (list.length === 0) setAdding(true);
  }, [list.length]);

  return (
    <div className="space-y-2">
      {list.map((a) => (
        <button
          key={a.id}
          onClick={() => onSelect(a.id)}
          aria-pressed={selected === a.id}
          className={cn(
            'flex w-full items-start gap-3 rounded-md border p-4 text-left transition-colors',
            selected === a.id ? 'border-fg bg-surface-2' : 'border-line bg-surface hover:border-line-strong',
          )}
        >
          <MapPin size={18} className="mt-0.5 shrink-0 text-muted" />
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-medium">{a.label}</span>
            <span className="block text-[13px] leading-5 text-muted">{a.formattedAddress}</span>
            {a.latitude == null && <span className="mt-1 block text-xs text-warning">No map location: distance checks are skipped</span>}
          </span>
          {selected === a.id && <Check size={18} className="shrink-0" />}
        </button>
      ))}

      {adding ? (
        <NewAddressForm
          onDone={(a) => {
            onSelect(a.id);
            setAdding(false);
          }}
          onCancel={list.length ? () => setAdding(false) : undefined}
        />
      ) : (
        <Button variant="secondary" icon={Plus} onClick={() => setAdding(true)}>
          Add new address
        </Button>
      )}
    </div>
  );
}

function NewAddressForm({ onDone, onCancel }: { onDone: (a: Address) => void; onCancel?: () => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [form, setForm] = useState<CreateAddressRequest>({ label: 'Home', street: '', city: '', state: '', pincode: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [locating, setLocating] = useState(false);

  const create = useMutation({
    mutationFn: addressApi.create,
    onSuccess: (a) => {
      qc.invalidateQueries({ queryKey: ['addresses'] });
      onDone(a);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.fieldErrors) setErrors(e.fieldErrors);
      else toast.error(e);
    },
  });

  const set = (k: keyof CreateAddressRequest) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  function pinLocation() {
    if (!navigator.geolocation) return toast.info('Location is not supported in this browser.');
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setForm((f) => ({ ...f, latitude: p.coords.latitude, longitude: p.coords.longitude }));
        setLocating(false);
      },
      () => {
        setLocating(false);
        toast.info('Could not get your location.');
      },
      { timeout: 10_000 },
    );
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    setErrors({});
    create.mutate({ ...form, isDefault: true });
  }

  return (
    <Card className="p-4">
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        <Input label="Label" name="label" value={form.label} onChange={set('label')} placeholder="Home, Work…" error={errors.label} />
        <Input label="Pincode" name="pincode" inputMode="numeric" value={form.pincode} onChange={set('pincode')} error={errors.pincode} />
        <div className="sm:col-span-2">
          <Input label="Street address" name="street" value={form.street} onChange={set('street')} placeholder="Flat, building, street" error={errors.street} />
        </div>
        <Input label="City" name="city" value={form.city} onChange={set('city')} error={errors.city} />
        <Input label="State" name="state" value={form.state} onChange={set('state')} error={errors.state} />
        <div className="flex flex-wrap items-center gap-2 sm:col-span-2">
          <Button type="button" variant="secondary" size="sm" icon={form.latitude != null ? Check : LocateFixed} loading={locating} onClick={pinLocation}>
            {form.latitude != null ? 'Location pinned' : 'Pin my location'}
          </Button>
          <span className="text-xs text-subtle">Needed for the delivery-distance check and live ETA.</span>
        </div>
        {errors.latitude && <p className="text-xs text-danger sm:col-span-2">{errors.latitude}</p>}
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" loading={create.isPending}>Save address</Button>
          {onCancel && <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>}
        </div>
      </form>
    </Card>
  );
}
