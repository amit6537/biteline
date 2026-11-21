import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Bike, Clock, IndianRupee, MapPin, Plus, ShoppingBag, Star, UtensilsCrossed } from 'lucide-react';
import { ApiError } from '@/api/client';
import { restaurantApi } from '@/api/endpoints';
import type { MenuItem, Restaurant } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';
import { useConfirm } from '@/components/Confirm';
import { useToast } from '@/components/Toast';
import { Badge, Button, EmptyState, ErrorState, QtyStepper, Skeleton, Thumb, VegMark } from '@/components/ui';
import { cartQtyOf, useCart, useCartActions } from '@/hooks/useCart';
import { cn, initials, money, relative } from '@/lib/format';

export function RestaurantPage() {
  const { id = '' } = useParams();
  const restaurant = useQuery({ queryKey: ['restaurant', id], queryFn: () => restaurantApi.get(id) });
  const menu = useQuery({ queryKey: ['menu', id], queryFn: () => restaurantApi.menu(id) });

  if (restaurant.isLoading) {
    return (
      <div>
        <Skeleton className="h-56 rounded-lg sm:h-72" />
        <Skeleton className="mt-6 h-8 w-1/2" />
        <Skeleton className="mt-3 h-4 w-1/3" />
      </div>
    );
  }
  if (restaurant.error) {
    const notFound = restaurant.error instanceof ApiError && restaurant.error.status === 404;
    return notFound ? (
      <EmptyState icon={UtensilsCrossed} title="Restaurant not found" action={<Link to="/"><Button variant="secondary">Back to home</Button></Link>} />
    ) : (
      <ErrorState error={restaurant.error} onRetry={() => restaurant.refetch()} />
    );
  }
  const r = restaurant.data!;

  return (
    <div className="mx-auto max-w-[1120px]">
      <Header r={r} dishes={menu.data ?? []} />
      <div className="mt-10 grid gap-16 grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_360px]">
        <section className="min-w-0">
          <Facts r={r} />
          {menu.isLoading ? (
            <div className="mt-8 space-y-3">
              {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28" />)}
            </div>
          ) : menu.error ? (
            <ErrorState error={menu.error} onRetry={() => menu.refetch()} />
          ) : (
            <Menu items={menu.data ?? []} restaurant={r} />
          )}
          <Reviews restaurantId={r.id} />
        </section>
        <aside className="hidden lg:sticky lg:top-28 lg:block lg:self-start">
          <OrderCard r={r} />
        </aside>
      </div>
      <CartBar restaurantId={r.id} />
    </div>
  );
}

/** Title above, then a 5-photo mosaic (cover + four dishes), like a listing page. */
function Header({ r, dishes }: { r: Restaurant; dishes: MenuItem[] }) {
  const photos = dishes.slice(0, 4);
  return (
    <header>
      <h1 className="text-[26px] font-semibold leading-tight">{r.name}</h1>
      <p className="mt-1.5 flex flex-wrap items-center gap-x-1.5 text-[15px]">
        {r.avgRating != null && r.totalReviews ? (
          <>
            <Star size={14} className="fill-fg" />
            <span className="font-semibold">{r.avgRating.toFixed(2)}</span>
            <span className="text-muted">·</span>
            <a href="#reviews" className="font-semibold underline">{r.totalReviews} reviews</a>
          </>
        ) : (
          <span className="font-semibold">New</span>
        )}
        <span className="text-muted">·</span>
        <span className="text-muted">{[r.cuisineType, r.city].filter(Boolean).join(' · ')}</span>
        {!r.isOpen && <Badge tone="danger" className="ml-1">Closed now</Badge>}
      </p>

      <div className="mt-6 grid h-[260px] grid-cols-4 grid-rows-2 gap-2 overflow-hidden rounded-lg sm:h-[400px]">
        <Thumb src={r.imageUrl} name={r.name} hint={r.cuisineType} seed={r.id} width={1400} style={{ viewTransitionName: `photo-${r.id}` }} className={cn('h-full w-full', photos.length ? 'col-span-4 row-span-2 sm:col-span-2' : 'col-span-4 row-span-2')} />
        {photos.map((d) => (
          <Thumb key={d.id} src={d.imageUrl} name={d.name} hint={d.category} width={500} className="hidden h-full w-full sm:block" />
        ))}
      </div>
    </header>
  );
}

/** Quick facts as icon rows (DESIGN.md amenity-row). */
function Facts({ r }: { r: Restaurant }) {
  const rows: [typeof Clock, string, string][] = [];
  if (r.avgPrepMinutes) rows.push([Clock, `About ${r.avgPrepMinutes} min to prepare`, 'Plus travel time to you.']);
  if (r.deliveryRadiusKm) rows.push([Bike, `Delivers within ${r.deliveryRadiusKm} km`, 'Check your address at checkout.']);
  rows.push([IndianRupee, r.minOrderAmount ? `Minimum order ${money(r.minOrderAmount)}` : 'No minimum order', 'Delivery fee shown in your cart.']);
  return (
    <div className="border-b border-line pb-8">
      {r.description && <p className="mb-6 text-base leading-relaxed text-body">{r.description}</p>}
      <ul className="space-y-5">
        {rows.map(([Icon, title, sub]) => (
          <li key={title} className="flex gap-5">
            <Icon size={24} strokeWidth={1.5} className="mt-0.5 shrink-0" />
            <div>
              <p className="text-base font-semibold">{title}</p>
              <p className="text-sm text-muted">{sub}</p>
            </div>
          </li>
        ))}
        <li className="flex gap-5">
          <MapPin size={24} strokeWidth={1.5} className="mt-0.5 shrink-0" />
          <div>
            <p className="text-base font-semibold">{r.city}</p>
            <p className="text-sm text-muted">{r.isOpen ? 'Open for orders now' : 'Closed right now'}</p>
          </div>
        </li>
      </ul>
    </div>
  );
}

/** Sticky right rail (DESIGN.md reservation-card): your cart for this place + the main CTA. */
function OrderCard({ r }: { r: Restaurant }) {
  const { user } = useAuth();
  const { data: cart } = useCart();
  const here = cart && cart.restaurantId === r.id && cart.totalItemCount > 0 ? cart : null;

  return (
    <div className="rounded-md border border-line p-6 shadow-[var(--shadow-float)]">
      {here ? (
        <>
          <p className="text-[22px] font-semibold">
            {money(here.subtotal)} <span className="text-base font-normal text-muted">· {here.totalItemCount} item{here.totalItemCount > 1 ? 's' : ''}</span>
          </p>
          <ul className="mt-4 space-y-2 rounded-sm border border-line-strong p-4 text-sm">
            {here.items.map((i) => (
              <li key={i.id} className="flex justify-between gap-3">
                <span className="truncate">{i.quantity} × {i.menuItemName}</span>
                <span className="tabular-nums">{money(i.itemTotal)}</span>
              </li>
            ))}
          </ul>
          <Link to="/cart" className="mt-4 block">
            <Button size="lg" className="w-full">Go to checkout</Button>
          </Link>
          <p className="mt-3 text-center text-sm text-muted">You won't be charged yet</p>
          <dl className="mt-5 space-y-3 border-t border-line pt-5 text-base">
            <div className="flex justify-between"><dt className="underline">Delivery fee</dt><dd>{here.deliveryFee ? money(here.deliveryFee) : 'Free'}</dd></div>
            <div className="flex justify-between font-semibold"><dt>Total</dt><dd>{money(here.totalAmount)}</dd></div>
          </dl>
        </>
      ) : (
        <>
          <p className="text-[22px] font-semibold">Start your order</p>
          <p className="mt-1 text-[15px] text-muted">
            {r.isOpen ? 'Add dishes from the menu. Your cart shows up here.' : 'This kitchen is closed right now. Check back soon.'}
          </p>
          {!user && (
            <Link to={`/login?next=${encodeURIComponent(`/restaurants/${r.id}`)}`} className="mt-5 block">
              <Button size="lg" className="w-full">Log in to order</Button>
            </Link>
          )}
          {cart && cart.totalItemCount > 0 && cart.restaurantId !== r.id && (
            <p className="mt-4 rounded-sm bg-surface-2 p-3 text-sm text-muted">
              Your cart has food from <span className="font-semibold text-fg">{cart.restaurantName}</span>. Adding a dish here will ask to replace it.
            </p>
          )}
        </>
      )}
    </div>
  );
}

function Menu({ items, restaurant }: { items: MenuItem[]; restaurant: Restaurant }) {
  const [vegOnly, setVegOnly] = useState(false);

  const groups = useMemo(() => {
    const map = new Map<string, MenuItem[]>();
    items
      .filter((i) => !vegOnly || i.isVegetarian)
      .forEach((i) => {
        const key = i.category?.trim() || 'Menu';
        map.set(key, [...(map.get(key) ?? []), i]);
      });
    return [...map.entries()];
  }, [items, vegOnly]);

  if (items.length === 0) {
    return <EmptyState icon={UtensilsCrossed} title="No dishes yet" text="This restaurant hasn't added its menu." />;
  }

  return (
    <div>
      <h2 className="mt-10 text-[22px] font-semibold">Menu</h2>
      <div className="sticky top-20 z-20 mb-6 mt-2 flex items-center gap-2 border-b border-line bg-bg py-3">
        <div className="no-scrollbar flex flex-1 gap-2 overflow-x-auto">
          {groups.map(([cat]) => (
            <a key={cat} href={`#cat-${slug(cat)}`} className="h-9 shrink-0 rounded-full border border-line px-4 text-sm font-medium leading-[34px] hover:border-fg">
              {cat}
            </a>
          ))}
        </div>
        <button
          onClick={() => setVegOnly((v) => !v)}
          aria-pressed={vegOnly}
          className={cn(
            'flex h-9 shrink-0 items-center gap-2 rounded-full border px-4 text-sm font-medium transition-colors',
            vegOnly ? 'border-fg bg-fg text-white' : 'border-line hover:border-fg',
          )}
        >
          <VegMark veg /> Veg only
        </button>
      </div>

      <div className="space-y-10">
        {groups.map(([cat, list]) => (
          <div key={cat} id={`cat-${slug(cat)}`} className="scroll-mt-40">
            <h3 className="mb-1 text-lg font-semibold">
              {cat} <span className="text-sm font-normal text-muted">{list.length}</span>
            </h3>
            <div className="divide-y divide-line">
              {list.map((item) => (
                <DishRow key={item.id} item={item} restaurant={restaurant} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-');

function DishRow({ item, restaurant }: { item: MenuItem; restaurant: Restaurant }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const confirm = useConfirm();
  const { data: cart } = useCart();
  const { add, setQty, clear } = useCartActions();
  const inCart = cartQtyOf(cart, item.id);
  const busy = add.isPending || setQty.isPending || clear.isPending;
  const canOrder = item.isAvailable && restaurant.isOpen;

  async function onAdd() {
    if (!user) {
      navigate(`/login?next=${encodeURIComponent(`/restaurants/${restaurant.id}`)}`);
      return;
    }
    if (user.role !== 'CUSTOMER') {
      toast.info('Only customer accounts can order.');
      return;
    }
    try {
      await add.mutateAsync({ menuItemId: item.id });
    } catch (e) {
      // 409 = the cart holds another restaurant's food
      if (e instanceof ApiError && e.status === 409) {
        if (
          await confirm({
            title: 'Start a new cart?',
            text: `${e.message} Clear it and add ${item.name} instead?`,
            confirmLabel: 'New cart',
          })
        ) {
          await clear.mutateAsync();
          await add.mutateAsync({ menuItemId: item.id }).catch(toast.error);
        }
      } else {
        toast.error(e);
      }
    }
  }

  return (
    <div className={cn('flex gap-5 py-5', !item.isAvailable && 'opacity-50')}>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <VegMark veg={item.isVegetarian} />
          {!item.isAvailable && <Badge>Sold out</Badge>}
        </div>
        <h4 className="mt-2 text-base font-semibold">{item.name}</h4>
        <p className="mt-0.5 text-base">{money(item.price)}</p>
        {item.description && <p className="mt-2 line-clamp-2 text-sm leading-5 text-muted">{item.description}</p>}
      </div>
      <div className="relative w-32 shrink-0">
        <Thumb src={item.imageUrl} name={item.name} hint={item.category} width={300} className="aspect-square w-32 rounded-md" />
        <div className="absolute inset-x-0 -bottom-3 flex justify-center">
          {inCart ? (
            <div className="rounded-full bg-bg px-1 shadow-[var(--shadow-card)]">
              <QtyStepper value={inCart.quantity} busy={busy} onChange={(q) => setQty.mutate({ itemId: inCart.id, quantity: q }, { onError: toast.error })} />
            </div>
          ) : (
            <button
              disabled={!canOrder || (busy && add.variables?.menuItemId === item.id)}
              onClick={onAdd}
              className="inline-flex h-9 items-center gap-1.5 rounded-full bg-bg px-4 text-sm font-semibold shadow-[var(--shadow-card)] transition-colors hover:bg-surface-2 disabled:text-subtle"
            >
              <Plus size={15} strokeWidth={2.5} /> Add
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function CartBar({ restaurantId }: { restaurantId: string }) {
  const { data: cart } = useCart();
  if (!cart || cart.totalItemCount === 0) return null;
  const here = cart.restaurantId === restaurantId;

  return (
    <div className={cn('fixed inset-x-0 bottom-[calc(64px+env(safe-area-inset-bottom))] z-30 border-t border-line bg-bg p-3 md:bottom-0 md:p-4', here && 'lg:hidden')}>
      <Link
        to="/cart"
        className="mx-auto flex h-14 max-w-xl items-center justify-between rounded-sm bg-primary px-5 text-white transition-colors hover:bg-primary-active"
      >
        <span className="flex items-center gap-3">
          <ShoppingBag size={18} />
          <span className="text-sm font-semibold">
            {cart.totalItemCount} item{cart.totalItemCount > 1 ? 's' : ''} · {money(cart.subtotal)}
            {!here && <span className="font-normal opacity-80"> from {cart.restaurantName}</span>}
          </span>
        </span>
        <span className="flex items-center gap-1.5 text-sm font-semibold">
          View cart <ArrowRight size={16} />
        </span>
      </Link>
    </div>
  );
}

function Reviews({ restaurantId }: { restaurantId: string }) {
  const summary = useQuery({ queryKey: ['rating', restaurantId], queryFn: () => restaurantApi.ratingSummary(restaurantId) });
  const reviews = useQuery({ queryKey: ['reviews', restaurantId], queryFn: () => restaurantApi.reviews(restaurantId) });
  const s = summary.data;

  return (
    <section id="reviews" className="mt-12 scroll-mt-24 border-t border-line pt-12">
      {summary.isLoading ? (
        <Skeleton className="h-32" />
      ) : s && s.totalReviews > 0 ? (
        <>
          {/* DESIGN.md rating-display: the one place type alone carries the page */}
          <div className="flex flex-col items-center text-center">
            <p className="text-[64px] font-bold leading-[1.1] tracking-[-1px]">{s.avgRating?.toFixed(2)}</p>
            <p className="mt-1 text-[15px] text-muted">from {s.totalReviews} review{s.totalReviews > 1 ? 's' : ''}</p>
          </div>
          <div className="mx-auto mt-8 max-w-sm space-y-1.5">
            {(['5', '4', '3', '2', '1'] as const).map((k) => {
              const n = s.distribution[k] ?? 0;
              const pct = s.totalReviews ? (n / s.totalReviews) * 100 : 0;
              return (
                <div key={k} className="flex items-center gap-3 text-xs">
                  <span className="w-2 tabular-nums">{k}</span>
                  <div className="h-1 flex-1 overflow-hidden rounded-full bg-line">
                    <div className="h-full rounded-full bg-fg" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </>
      ) : (
        <div>
          <h2 className="text-[22px] font-semibold">No reviews yet</h2>
          <p className="mt-1 text-[15px] text-muted">Be the first to order and tell others how it was.</p>
        </div>
      )}

      {reviews.data && reviews.data.content.length > 0 && (
        <ul className="mt-12 grid gap-x-16 gap-y-10 sm:grid-cols-2">
          {reviews.data.content.map((rv) => (
            <li key={rv.id}>
              <div className="flex items-center gap-3">
                <span className="grid size-12 place-items-center rounded-full bg-fg text-sm font-semibold text-white">{initials(rv.reviewerName)}</span>
                <div>
                  <p className="text-base font-semibold">{rv.reviewerName}</p>
                  <p className="text-sm text-muted">{relative(rv.createdAt)}{rv.edited && ' · edited'}</p>
                </div>
              </div>
              <p className="mt-3 flex items-center gap-0.5">
                {Array.from({ length: 5 }, (_, i) => (
                  <Star key={i} size={10} className={i < rv.foodRating ? 'fill-fg text-fg' : 'text-line-strong'} />
                ))}
              </p>
              {rv.comment && <p className="mt-2 text-base leading-6 text-body">{rv.comment}</p>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
