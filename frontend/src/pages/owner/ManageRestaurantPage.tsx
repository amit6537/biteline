import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, Pencil, Plus, Store, Trash2, UtensilsCrossed } from 'lucide-react';
import { ApiError } from '@/api/client';
import { ownerApi } from '@/api/endpoints';
import type { MenuItem, MenuItemRequest, Restaurant } from '@/api/types';
import { useConfirm } from '@/components/Confirm';
import { useToast } from '@/components/Toast';
import { Badge, Button, Card, EmptyState, ErrorState, Input, PageHeader, Skeleton, Textarea, Thumb, VegMark } from '@/components/ui';
import { Switch } from '@/components/Switch';
import { Animated } from '@/components/Animated';
import { OWNER_KEYS, useMyRestaurants } from '@/hooks/useOwner';
import { cn, money } from '@/lib/format';
import { ImagePicker } from './ImagePicker';
import { RestaurantForm } from './RestaurantForm';

/* /owner/restaurants/new */
export function NewRestaurantPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader back="/owner" title="Add a restaurant" subtitle="You can add the menu right after." />
      <RestaurantForm
        submitLabel="Create restaurant"
        onSubmit={async (body) => {
          const r = await ownerApi.create(body);
          qc.invalidateQueries({ queryKey: OWNER_KEYS.restaurants });
          toast.success(`${r.name} created. Now add some dishes.`);
          navigate(`/owner/restaurants/${r.id}`, { replace: true });
        }}
      />
    </div>
  );
}

/* /owner/restaurants/:id — Menu tab + Settings tab */
export function ManageRestaurantPage() {
  const { id = '' } = useParams();
  const restaurants = useMyRestaurants();
  const [tab, setTab] = useState<'menu' | 'settings'>('menu');
  const r = restaurants.data?.find((x) => x.id === id);

  if (restaurants.isLoading) return <Skeleton className="h-60" />;
  if (restaurants.error) return <ErrorState error={restaurants.error} onRetry={() => restaurants.refetch()} />;
  if (!r) {
    return <EmptyState icon={Store} title="Restaurant not found" text="It may have been deleted, or it belongs to another account." action={<Link to="/owner"><Button variant="secondary">Back to kitchen</Button></Link>} />;
  }

  return (
    <div>
      <PageHeader
        back="/owner"
        title={r.name}
        subtitle={r.cuisineType ?? r.city}
        action={
          <Link to={`/restaurants/${r.id}`}>
            <Button variant="ghost" size="sm" icon={ExternalLink}>Customer view</Button>
          </Link>
        }
      />
      <div className="mb-6 flex gap-1 border-b border-line">
        {(['menu', 'settings'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={cn('-mb-px border-b-2 px-4 py-2.5 text-sm font-medium capitalize transition-colors', tab === t ? 'border-fg text-fg' : 'border-transparent text-muted hover:text-fg')}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === 'menu' ? <MenuEditor restaurant={r} /> : <Settings restaurant={r} />}
    </div>
  );
}

function Settings({ restaurant: r }: { restaurant: Restaurant }) {
  const qc = useQueryClient();
  const toast = useToast();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const remove = useMutation({
    mutationFn: () => ownerApi.remove(r.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: OWNER_KEYS.restaurants });
      toast.success(`${r.name} deleted.`);
      navigate('/owner', { replace: true });
    },
    onError: toast.error,
  });

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <RestaurantForm
        initial={r}
        submitLabel="Save changes"
        onSubmit={async ({ city: _city, ...body }) => {
          const updated = await ownerApi.update(r.id, body);
          qc.setQueryData<Restaurant[]>(OWNER_KEYS.restaurants, (old) => old?.map((x) => (x.id === updated.id ? updated : x)));
          qc.invalidateQueries({ queryKey: ['restaurant', r.id] });
          qc.invalidateQueries({ queryKey: ['restaurants'] });
          toast.success('Saved.');
        }}
      />
      <Card className="flex items-center gap-4 border-danger/30 p-5">
        <div className="flex-1">
          <p className="text-sm font-medium">Delete restaurant</p>
          <p className="text-xs text-muted">Removes it and its menu. Can't be undone.</p>
        </div>
        <Button variant="danger" size="sm" icon={Trash2} loading={remove.isPending} onClick={async () => (await confirm({ title: `Delete ${r.name}?`, text: 'The restaurant and its whole menu are removed. This cannot be undone.', confirmLabel: 'Delete for good', danger: true })) && remove.mutate()}>
          Delete
        </Button>
      </Card>
    </div>
  );
}

// ── Menu ─────────────────────────────────────────────────────

function MenuEditor({ restaurant: r }: { restaurant: Restaurant }) {
  const menu = useQuery({ queryKey: OWNER_KEYS.menu(r.id), queryFn: () => ownerApi.menu(r.id) });
  const [editing, setEditing] = useState<MenuItem | 'new' | null>(null);

  if (menu.isLoading) return <div className="space-y-3">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-20" />)}</div>;
  if (menu.error) return <ErrorState error={menu.error} onRetry={() => menu.refetch()} />;

  const items = menu.data ?? [];
  const categories = [...new Set(items.map((i) => i.category || 'Other'))];

  return (
    <div className="grid gap-8 grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_400px]">
      <div>
        <div className="mb-4 flex items-center justify-between">
          <p className="text-sm text-muted">
            {items.length} dishes · {items.filter((i) => !i.isAvailable).length} sold out
          </p>
          <Button icon={Plus} size="sm" onClick={() => setEditing('new')}>Add dish</Button>
        </div>
        {items.length === 0 ? (
          <EmptyState icon={UtensilsCrossed} title="No dishes yet" text="Add your first dish so customers can order." action={<Button icon={Plus} onClick={() => setEditing('new')}>Add dish</Button>} />
        ) : (
          <div className="space-y-6">
            {categories.map((cat) => (
              <section key={cat}>
                <h3 className="mb-2 text-sm font-semibold text-muted">{cat}</h3>
                <Animated className="divide-y divide-line rounded-md border border-line bg-surface">
                  {items.filter((i) => (i.category || 'Other') === cat).map((item) => (
                    <MenuRow key={item.id} item={item} restaurantId={r.id} onEdit={() => setEditing(item)} active={editing !== 'new' && editing?.id === item.id} />
                  ))}
                </Animated>
              </section>
            ))}
          </div>
        )}
      </div>

      <aside className="lg:sticky lg:top-24 lg:self-start">
        {editing ? (
          <ItemForm
            key={editing === 'new' ? 'new' : editing.id}
            restaurantId={r.id}
            item={editing === 'new' ? undefined : editing}
            categories={categories}
            onDone={() => setEditing(null)}
          />
        ) : (
          <Card className="p-5 text-sm text-muted">
            Pick a dish to edit it, or <button className="font-medium text-fg underline-offset-4 hover:underline" onClick={() => setEditing('new')}>add a new one</button>.
            <p className="mt-3 text-xs text-subtle">Tip: switch "Available" off when a dish runs out. Customers see it as sold out.</p>
          </Card>
        )}
      </aside>
    </div>
  );
}

function MenuRow({ item, restaurantId, onEdit, active }: { item: MenuItem; restaurantId: string; onEdit: () => void; active: boolean }) {
  const qc = useQueryClient();
  const toast = useToast();
  const confirm = useConfirm();
  const refresh = () => {
    qc.invalidateQueries({ queryKey: OWNER_KEYS.menu(restaurantId) });
    qc.invalidateQueries({ queryKey: ['menu', restaurantId] });
  };
  const toggle = useMutation({
    mutationFn: () => ownerApi.updateItem(restaurantId, item.id, { isAvailable: !item.isAvailable }),
    onSuccess: refresh,
    onError: toast.error,
  });
  const remove = useMutation({
    mutationFn: () => ownerApi.removeItem(restaurantId, item.id),
    onSuccess: () => {
      refresh();
      toast.success(`${item.name} removed.`);
    },
    onError: toast.error,
  });

  return (
    <div className={cn('flex items-center gap-3 p-3', active && 'bg-surface-2')}>
      <Thumb src={item.imageUrl} name={item.name} hint={item.category} width={160} className={cn('size-14 shrink-0 rounded-sm', !item.isAvailable && 'opacity-40 grayscale')} />
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-2 text-sm font-medium">
          <VegMark veg={item.isVegetarian} /> <span className="truncate">{item.name}</span>
          {!item.isAvailable && <Badge>Sold out</Badge>}
        </p>
        <p className="text-[13px] text-muted">{money(item.price)}</p>
      </div>
      <Switch
        checked={item.isAvailable}
        onCheckedChange={() => toggle.mutate()}
        disabled={toggle.isPending}
        label={item.isAvailable ? `${item.name}: available (switch off to mark sold out)` : `${item.name}: sold out (switch on to make available)`}
      />
      <Button variant="ghost" size="sm" icon={Pencil} aria-label={`Edit ${item.name}`} onClick={onEdit} />
      <Button variant="ghost" size="sm" icon={Trash2} aria-label={`Delete ${item.name}`} loading={remove.isPending} onClick={async () => (await confirm({ title: `Delete ${item.name}?`, text: 'Tip: to hide it for today only, switch it to sold out instead.', confirmLabel: 'Delete', danger: true })) && remove.mutate()} />
    </div>
  );
}

function ItemForm({ restaurantId, item, categories, onDone }: { restaurantId: string; item?: MenuItem; categories: string[]; onDone: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [f, setF] = useState({
    name: item?.name ?? '',
    description: item?.description ?? '',
    price: item ? String(item.price) : '',
    category: item?.category ?? categories[0] ?? '',
    imageUrl: item?.imageUrl ?? '',
    isVegetarian: item?.isVegetarian ?? true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = (k: 'name' | 'description' | 'price' | 'category') => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));

  const save = useMutation({
    mutationFn: () => {
      const body: MenuItemRequest = {
        name: f.name.trim(),
        description: f.description.trim() || undefined,
        price: Number(f.price),
        category: f.category.trim(),
        imageUrl: item ? f.imageUrl : f.imageUrl || undefined,
        isVegetarian: f.isVegetarian,
      };
      return item ? ownerApi.updateItem(restaurantId, item.id, body) : ownerApi.addItem(restaurantId, body);
    },
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: OWNER_KEYS.menu(restaurantId) });
      qc.invalidateQueries({ queryKey: ['menu', restaurantId] });
      toast.success(item ? `${saved.name} updated.` : `${saved.name} added to the menu.`);
      onDone();
    },
    onError: (e) => {
      if (e instanceof ApiError && e.fieldErrors) setErrors(e.fieldErrors);
      else toast.error(e);
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setErrors({});
    save.mutate();
  }

  return (
    <Card className="p-5">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <h2 className="text-base font-semibold">{item ? 'Edit dish' : 'New dish'}</h2>
        <Input label="Name" value={f.name} onChange={set('name')} error={errors.name} placeholder="Paneer Butter Masala" />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Price (₹)" inputMode="decimal" value={f.price} onChange={set('price')} error={errors.price} placeholder="249" />
          <div>
            <Input label="Category" list="menu-categories" value={f.category} onChange={set('category')} error={errors.category} placeholder="Mains" />
            <datalist id="menu-categories">{categories.map((c) => <option key={c} value={c} />)}</datalist>
          </div>
        </div>
        <Textarea label="Description" value={f.description} onChange={set('description')} maxLength={300} placeholder="Short and tasty" />
        <div>
          <span className="mb-1.5 block text-[13px] font-medium text-muted">Type</span>
          <div className="grid grid-cols-2 gap-2">
            {[true, false].map((veg) => (
              <button
                key={String(veg)}
                type="button"
                onClick={() => setF((x) => ({ ...x, isVegetarian: veg }))}
                className={cn('flex h-10 items-center justify-center gap-2 rounded-sm border text-sm', f.isVegetarian === veg ? 'border-fg bg-surface-2' : 'border-line text-muted')}
              >
                <VegMark veg={veg} /> {veg ? 'Veg' : 'Non-veg'}
              </button>
            ))}
          </div>
        </div>
        <ImagePicker value={f.imageUrl} onChange={(imageUrl) => setF((x) => ({ ...x, imageUrl }))} name={f.name} hint={f.category} />
        <div className="flex gap-2 pt-1">
          <Button type="submit" loading={save.isPending} disabled={!f.name.trim() || !f.price || !f.category.trim()}>
            {item ? 'Save' : 'Add to menu'}
          </Button>
          <Button type="button" variant="ghost" onClick={onDone}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}
