import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Beef,
  CookingPot,
  Fish,
  IceCreamCone,
  LocateFixed,
  Pizza,
  Salad,
  Sandwich,
  Search,
  Soup,
  Star,
  Store,
  UtensilsCrossed,
  X,
  type LucideIcon,
} from 'lucide-react';
import { restaurantApi } from '@/api/endpoints';
import type { Restaurant } from '@/api/types';
import { useToast } from '@/components/Toast';
import { EmptyState, ErrorState, Skeleton, Spinner, Thumb } from '@/components/ui';
import { cn, km } from '@/lib/format';

/*
 * Home, in the DESIGN.md marketplace pattern:
 *   - pill search bar: "Restaurant" | "City" segments + the round brand-colour search button
 *   - category strip of cuisines (icon + label, ink underline when active)
 *   - photo-first cards, 16px gutters, 4 across on desktop
 *
 * Data: by CITY (GET /api/restaurants?city=… or /search) or NEAR ME (/nearby).
 */

const CITY_KEY = 'fd.city';
type Coords = { lat: number; lng: number };

/** Cuisine filters — matched against the restaurant's cuisine, name and description. */
const CATEGORIES: { label: string; icon: LucideIcon; match?: RegExp }[] = [
  { label: 'All', icon: UtensilsCrossed },
  { label: 'Indian', icon: Soup, match: /indian|tiffin|punjabi|mughlai|thali|curry/ },
  { label: 'Biryani', icon: CookingPot, match: /biryani|hyderabad|dum/ },
  { label: 'Pizza', icon: Pizza, match: /pizza|italian/ },
  { label: 'Seafood', icon: Fish, match: /seafood|fish|coastal|konkani|malvani/ },
  { label: 'Healthy', icon: Salad, match: /healthy|salad|bowl|vegan/ },
  { label: 'Street food', icon: Sandwich, match: /street|snack|chaat|pav|bites/ },
  { label: 'Burgers & grill', icon: Beef, match: /burger|grill|bbq|smoke|diner|kebab/ },
  { label: 'Desserts', icon: IceCreamCone, match: /dessert|sweet|bakery|cake|ice cream/ },
];

function savedCity() {
  try {
    return localStorage.getItem(CITY_KEY) || 'Mumbai';
  } catch {
    return 'Mumbai';
  }
}

function useDebounced<T>(value: T, ms = 300) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export function HomePage() {
  const toast = useToast();
  const [city, setCity] = useState(savedCity);
  const [cityDraft, setCityDraft] = useState(city);
  const [name, setName] = useState('');
  const [category, setCategory] = useState('All');
  const [coords, setCoords] = useState<Coords | null>(null);
  const [locating, setLocating] = useState(false);
  const term = useDebounced(name.trim());

  const nearbyQuery = useQuery({
    queryKey: ['restaurants', 'nearby', coords],
    queryFn: () => restaurantApi.nearby(coords!.lat, coords!.lng, 10),
    enabled: !!coords,
  });

  const cityQuery = useQuery({
    queryKey: ['restaurants', 'city', city, term],
    queryFn: () => (term ? restaurantApi.search(city, term) : restaurantApi.byCity(city)),
    enabled: !coords,
  });

  function applyCity() {
    const c = cityDraft.trim();
    if (!c) return;
    setCity(c);
    setCoords(null);
    try {
      localStorage.setItem(CITY_KEY, c);
    } catch {
      /* ignore */
    }
  }

  function locateMe() {
    if (!navigator.geolocation) {
      toast.info('Your browser does not support location.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setLocating(false);
      },
      () => {
        setLocating(false);
        toast.info('Location permission denied. Showing restaurants by city instead.');
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 },
    );
  }

  // Unify both result types into one list, then apply the cuisine filter
  const all: { r: Restaurant; distanceKm?: number; etaMin?: number }[] = coords
    ? (nearbyQuery.data ?? [])
        .filter((n) => !term || n.restaurant.name.toLowerCase().includes(term.toLowerCase()))
        .map((n) => ({ r: n.restaurant, distanceKm: n.distanceKm, etaMin: n.estimatedDeliveryMinutes }))
    : (cityQuery.data?.content ?? []).map((r) => ({ r }));

  const cat = CATEGORIES.find((c) => c.label === category);
  const rows = cat?.match
    ? all.filter(({ r }) => cat.match!.test(`${r.cuisineType ?? ''} ${r.name} ${r.description ?? ''}`.toLowerCase()))
    : all;

  const active = coords ? nearbyQuery : cityQuery;

  return (
    <div>
      {/* ── Search pill ── */}
      <section className="flex flex-col items-center">
        <h1 className="mb-6 text-center text-[28px] font-bold leading-tight sm:text-[32px]">What are you craving today?</h1>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            applyCity();
          }}
          className="flex w-full max-w-3xl items-center rounded-full border border-line bg-bg shadow-[var(--shadow-card)]"
        >
          <label className="min-w-0 flex-[1.4] rounded-full py-2.5 pl-7 pr-4 hover:bg-surface-2">
            <span className="block text-xs font-semibold">Restaurant</span>
            <span className="flex items-center">
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Search by name"
                className="w-full bg-transparent text-sm text-fg placeholder:text-muted outline-none"
              />
              {name && (
                <button type="button" onClick={() => setName('')} aria-label="Clear search" className="rounded-full p-0.5 text-muted hover:bg-surface-3">
                  <X size={14} />
                </button>
              )}
            </span>
          </label>
          <span className="h-8 w-px bg-line" />
          <label className="min-w-0 flex-1 rounded-full py-2.5 pl-6 pr-2 hover:bg-surface-2">
            <span className="block text-xs font-semibold">{coords ? 'Near you' : 'City'}</span>
            <input
              value={coords ? 'Current location' : cityDraft}
              onChange={(e) => {
                setCoords(null);
                setCityDraft(e.target.value);
              }}
              onBlur={applyCity}
              placeholder="Which city?"
              className="w-full bg-transparent text-sm text-fg placeholder:text-muted outline-none"
            />
          </label>
          <button
            type="button"
            onClick={() => (coords ? setCoords(null) : locateMe())}
            title={coords ? 'Stop using my location' : 'Use my location'}
            aria-label={coords ? 'Stop using my location' : 'Use my location'}
            className={cn(
              'mr-1 grid size-10 shrink-0 place-items-center rounded-full transition-colors',
              coords ? 'bg-fg text-white' : 'text-fg hover:bg-surface-3',
            )}
          >
            {locating ? <Spinner className="text-current" /> : <LocateFixed size={18} />}
          </button>
          {/* the search orb: the hottest colour moment on the page */}
          <button type="submit" aria-label="Search" className="mr-2 grid size-12 shrink-0 place-items-center rounded-full bg-primary text-white transition-colors hover:bg-primary-active">
            <Search size={18} strokeWidth={2.5} />
          </button>
        </form>
      </section>

      {/* ── Category strip ── */}
      <nav className="no-scrollbar -mx-4 mt-8 flex gap-8 overflow-x-auto border-b border-line px-4 sm:mx-0 sm:px-0" aria-label="Cuisines">
        {CATEGORIES.map(({ label, icon: Icon }) => {
          const on = category === label;
          return (
            <button
              key={label}
              onClick={() => setCategory(label)}
              aria-pressed={on}
              className={cn(
                'group flex shrink-0 flex-col items-center gap-2 border-b-2 pb-3 pt-1 text-xs font-semibold transition-colors',
                on ? 'border-fg text-fg' : 'border-transparent text-muted hover:border-line-strong hover:text-fg',
              )}
            >
              <Icon size={24} strokeWidth={1.6} />
              {label}
            </button>
          );
        })}
      </nav>

      {/* ── Results ── */}
      <div className="mb-5 mt-8 flex items-baseline justify-between gap-4">
        <h2 className="text-[22px] font-semibold">
          {coords ? 'Delivering to you' : `Restaurants in ${city}`}
          {term && <span className="text-muted"> · “{term}”</span>}
        </h2>
        {!active.isLoading && <span className="shrink-0 text-sm text-muted">{rows.length} places</span>}
      </div>

      {active.isLoading ? (
        <Grid>
          {Array.from({ length: 8 }, (_, i) => (
            <div key={i}>
              <Skeleton className="aspect-[20/19] rounded-md" />
              <Skeleton className="mt-3 h-4 w-2/3" />
              <Skeleton className="mt-2 h-3 w-1/3" />
            </div>
          ))}
        </Grid>
      ) : active.error ? (
        <ErrorState error={active.error} onRetry={() => active.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={Store}
          title={term || cat?.match ? 'No matches' : coords ? 'Nothing delivers here yet' : `No restaurants in ${city} yet`}
          text={
            term || cat?.match
              ? 'Try a different name or cuisine.'
              : 'Try another city, or add demo data with "npm run seed" in the frontend folder (it creates restaurants in Mumbai).'
          }
        />
      ) : (
        <Grid>
          {rows.map(({ r, distanceKm, etaMin }) => (
            <RestaurantCard key={r.id} r={r} distanceKm={distanceKm} etaMin={etaMin} />
          ))}
        </Grid>
      )}
    </div>
  );
}

function Grid({ children }: { children: React.ReactNode }) {
  return <div className="grid gap-x-4 gap-y-10 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">{children}</div>;
}

/** DESIGN.md property-card: photo first, floating pill badge, 3–4 lines of meta under it. */
function RestaurantCard({ r, distanceKm, etaMin }: { r: Restaurant; distanceKm?: number; etaMin?: number }) {
  const minutes = etaMin ?? (r.avgPrepMinutes ? r.avgPrepMinutes + 15 : null);
  const topRated = (r.avgRating ?? 0) >= 4.5 && (r.totalReviews ?? 0) >= 3;
  return (
    <Link to={`/restaurants/${r.id}`} viewTransition className="group block">
      <div className="relative overflow-hidden rounded-md">
        <Thumb
          src={r.imageUrl}
          name={r.name}
          hint={r.cuisineType}
          seed={r.id}
          width={700}
          style={{ viewTransitionName: `photo-${r.id}` }}
          className={cn('aspect-[20/19] w-full transition-transform duration-300 group-hover:scale-[1.03]', !r.isOpen && 'grayscale')}
        />
        {(topRated || !r.isOpen) && (
          <span className="absolute left-3 top-3 rounded-full bg-bg px-3 py-1.5 text-xs font-semibold shadow-[var(--shadow-card)]">
            {!r.isOpen ? 'Closed now' : 'Top rated'}
          </span>
        )}
      </div>
      <div className="mt-3 flex items-start justify-between gap-3">
        <h3 className="truncate text-[15px] font-semibold">{r.name}</h3>
        {r.avgRating != null && r.totalReviews ? (
          <span className="inline-flex shrink-0 items-center gap-1 text-[15px]">
            <Star size={13} className="fill-fg" /> {r.avgRating.toFixed(2)}
          </span>
        ) : (
          <span className="shrink-0 text-[15px]">New</span>
        )}
      </div>
      <p className="truncate text-[15px] text-muted">{r.cuisineType || r.city}</p>
      <p className="text-[15px] text-muted">
        {[minutes != null && `${minutes} min`, distanceKm != null && km(distanceKm)].filter(Boolean).join(' · ') || r.city}
      </p>
      <p className="mt-1 text-[15px]">
        {r.minOrderAmount ? (
          <>
            <span className="font-semibold">₹{r.minOrderAmount}</span> <span className="text-muted">minimum</span>
          </>
        ) : (
          <span className="font-semibold">No minimum order</span>
        )}
      </p>
    </Link>
  );
}
