import { Suspense, useEffect, useRef } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Bell, Bike, ChefHat, CircleUser, Compass, LogIn, LogOut, Menu, ReceiptText, Search, ShieldCheck, ShoppingBag, User, UtensilsCrossed } from 'lucide-react';
import { notificationApi } from '@/api/endpoints';
import { useAuth } from '@/auth/AuthContext';
import { useCart } from '@/hooks/useCart';
import { useIncomingOrders, useNewOrderAlerts } from '@/hooks/useOwner';
import { cn, initials } from '@/lib/format';
import { ROLE_HOME, ROLE_LABEL } from '@/lib/roles';
import { Skeleton } from './ui';

/*
 * Page frame, following DESIGN.md "top-nav" + "footer-light":
 *   - 80px white bar, hairline bottom border
 *   - brand mark flush left, role tabs in the middle, account pill on the right
 *   - a plain white footer with three link columns and a legal band
 *   - phones (< 768px): a bottom tab bar replaces the top-right icons
 */

export function AppShell() {
  const { user } = useAuth();
  return (
    <div className="flex min-h-dvh flex-col bg-bg pb-[calc(64px+env(safe-area-inset-bottom))] md:pb-0">
      {user?.role === 'RESTAURANT_OWNER' && <OwnerAlerts />}
      <TopBar />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 pb-24 pt-6 sm:px-6 sm:pt-8 lg:px-10">
        {/* pages are loaded on demand (React.lazy) — show a skeleton meanwhile */}
        <Suspense fallback={<PageLoading />}>
          <Outlet />
        </Suspense>
      </main>
      <Footer />
      <TabBar />
    </div>
  );
}

function PageLoading() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Loading">
      <Skeleton className="h-9 w-1/3" />
      <Skeleton className="h-5 w-1/4" />
      <Skeleton className="h-64 rounded-lg" />
    </div>
  );
}

/** Mounted only for owners: dings + toasts when a new order arrives, on every page. */
function OwnerAlerts() {
  useNewOrderAlerts();
  return null;
}

export function BrandMark({ to = '/' }: { to?: string }) {
  return (
    <Link to={to} viewTransition className="flex items-center gap-2 text-primary" aria-label="Food Delivery home">
      <span className="grid size-9 place-items-center rounded-sm bg-primary text-white">
        <UtensilsCrossed size={18} strokeWidth={2.25} />
      </span>
      <span className="hidden text-xl font-bold tracking-[-0.4px] md:inline">fooddelivery</span>
    </Link>
  );
}

function TopBar() {
  const { user } = useAuth();
  const role = user?.role;

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-bg">
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center gap-2 px-4 sm:px-6 md:h-20 lg:px-10">
        <div className="flex flex-1 items-center">
          <BrandMark to={role ? ROLE_HOME[role] : '/'} />
        </div>

        {/* Centre tabs: the user's main places (DESIGN.md product tabs: ink + underline when active) */}
        {user && role !== 'CUSTOMER' && (
          <nav className="hidden items-center gap-1 md:flex">
            {role === 'RESTAURANT_OWNER' && <KitchenTab />}
            {role === 'DELIVERY_PARTNER' && <Tab to="/rider" label="Deliveries" icon={Bike} />}
            {role === 'ADMIN' && <Tab to="/admin" label="Operations" icon={ShieldCheck} />}
            <Tab to="/" label="Explore" icon={Compass} end />
          </nav>
        )}

        <div className="flex flex-1 items-center justify-end gap-1">
          {user ? (
            <>
              {/* on phones these live in the bottom tab bar instead */}
              <div className="hidden items-center gap-1 md:flex">
                {role === 'CUSTOMER' && <NavIcon to="/orders" label="Orders" icon={ReceiptText} />}
                <NotificationBell />
                {role === 'CUSTOMER' && <CartButton />}
              </div>
              <ProfileMenu />
            </>
          ) : (
            <>
              <Link to="/register" className="hidden rounded-full px-4 py-3 text-sm font-semibold hover:bg-surface-2 md:block">
                Become a partner
              </Link>
              <ProfileMenu />
            </>
          )}
        </div>
      </div>
    </header>
  );
}

function Tab({ to, label, icon: Icon, badge, end }: { to: string; label: string; icon: typeof Bell; badge?: number; end?: boolean }) {
  return (
    <NavLink
      to={to}
      end={end}
      viewTransition
      className={({ isActive }) =>
        cn(
          'relative flex h-20 items-center gap-2 px-4 text-base font-semibold transition-colors',
          isActive ? 'text-fg after:absolute after:inset-x-4 after:bottom-0 after:h-0.5 after:bg-fg' : 'text-muted hover:text-fg',
        )
      }
    >
      <Icon size={20} strokeWidth={1.75} />
      {label}
      {!!badge && <CountBadge n={badge} className="relative" />}
    </NavLink>
  );
}

function KitchenTab() {
  const { data } = useIncomingOrders();
  return <Tab to="/owner" label="Kitchen" icon={ChefHat} badge={data?.length} />;
}

function CountBadge({ n, className }: { n: number; className?: string }) {
  return (
    <span className={cn('grid h-5 min-w-5 place-items-center rounded-full bg-primary px-1.5 text-[11px] font-bold leading-none text-white', className)}>
      {n > 9 ? '9+' : n}
    </span>
  );
}

function NavIcon({
  to,
  label,
  icon: Icon,
  badge,
  end,
  className,
}: {
  to: string;
  label: string;
  icon: typeof Bell;
  badge?: number;
  end?: boolean;
  className?: string;
}) {
  return (
    <NavLink
      to={to}
      end={end}
      viewTransition
      aria-label={label}
      title={label}
      className={({ isActive }) =>
        cn(
          'relative grid size-11 place-items-center rounded-full transition-colors hover:bg-surface-2',
          isActive ? 'text-fg' : 'text-body',
          className,
        )
      }
    >
      <Icon size={20} strokeWidth={1.75} />
      {!!badge && <CountBadge n={badge} className="absolute -right-0.5 -top-0.5 border-2 border-bg" />}
    </NavLink>
  );
}

function NotificationBell() {
  const { data } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: notificationApi.unreadCount,
    refetchInterval: 30_000, // the backend has no push yet → poll every 30 s
  });
  return <NavIcon to="/notifications" label="Notifications" icon={Bell} badge={data?.unreadCount} />;
}

function CartButton() {
  const { data } = useCart();
  return <NavIcon to="/cart" label="Cart" icon={ShoppingBag} badge={data?.totalItemCount} />;
}

/**
 * The "☰ + avatar" pill and its menu, built on the browser's own Popover API
 * (popover="auto"): click-outside and Esc close it, and it renders above
 * everything — with 0 kB of JavaScript library. Position + animation: .account-menu in index.css.
 */
function ProfileMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const menu = useRef<HTMLDivElement>(null);
  const close = () => {
    try {
      menu.current?.hidePopover();
    } catch {
      /* already closed */
    }
  };

  useEffect(close, [pathname]); // navigated → close

  return (
    <>
      <button
        popoverTarget="account-menu"
        aria-haspopup="menu"
        aria-label="Account menu"
        className="ml-1 flex h-11 items-center gap-3 rounded-full border border-line bg-bg pl-3.5 pr-1.5 transition-shadow hover:shadow-[var(--shadow-card)] md:h-12"
      >
        <Menu size={17} strokeWidth={2} />
        <span className={cn('grid size-8 place-items-center rounded-full text-xs font-semibold text-white', user ? 'bg-fg' : 'bg-muted')}>
          {user ? initials(user.name) : <User size={18} fill="currentColor" strokeWidth={0} />}
        </span>
      </button>

      <div
        ref={menu}
        id="account-menu"
        popover="auto"
        role="menu"
        // any link/button inside closes the menu
        onClick={(e) => (e.target as HTMLElement).closest('a,button') && close()}
        className="account-menu w-64 overflow-hidden rounded-md border border-line bg-bg py-2 text-fg shadow-[var(--shadow-float)]"
      >
        {user ? (
          <>
            <div className="px-4 pb-3 pt-1">
              <p className="truncate text-sm font-semibold">{user.name}</p>
              <p className="truncate text-[13px] text-muted">{user.email}</p>
              <p className="mt-1 text-xs text-muted">{ROLE_LABEL[user.role]}</p>
            </div>
            <hr className="my-1 border-line" />
            {user.role === 'CUSTOMER' && <MenuLink to="/orders" icon={ReceiptText}>Your orders</MenuLink>}
            {user.role === 'RESTAURANT_OWNER' && <MenuLink to="/owner" icon={ChefHat}>Kitchen dashboard</MenuLink>}
            {user.role === 'DELIVERY_PARTNER' && <MenuLink to="/rider" icon={Bike}>Deliveries</MenuLink>}
            {user.role === 'ADMIN' && <MenuLink to="/admin" icon={ShieldCheck}>Operations</MenuLink>}
            <MenuLink to="/notifications" icon={Bell}>Notifications</MenuLink>
            <MenuLink to="/account" icon={User}>Account</MenuLink>
            <hr className="my-1 border-line" />
            <button
              role="menuitem"
              onClick={async () => {
                await logout();
                navigate('/');
              }}
              className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm hover:bg-surface-2 focus-visible:bg-surface-2 focus-visible:outline-none"
            >
              <LogOut size={16} /> Log out
            </button>
          </>
        ) : (
          <>
            <MenuLink to="/register" bold>Sign up</MenuLink>
            <MenuLink to="/login">Log in</MenuLink>
            <hr className="my-1 border-line" />
            <MenuLink to="/register">Run a kitchen with us</MenuLink>
            <MenuLink to="/register">Deliver with us</MenuLink>
          </>
        )}
      </div>
    </>
  );
}

function MenuLink({ to, icon: Icon, children, bold }: { to: string; icon?: typeof User; children: string; bold?: boolean }) {
  return (
    <Link
      role="menuitem"
      to={to}
      viewTransition
      className={cn('flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-surface-2 focus-visible:bg-surface-2 focus-visible:outline-none', bold && 'font-semibold')}
    >
      {Icon && <Icon size={16} />} {children}
    </Link>
  );
}

// ── Bottom tab bar (phones only) ─────────────────────────────

function TabBar() {
  const { user } = useAuth();
  const role = user?.role;
  const { data: unread } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: notificationApi.unreadCount,
    enabled: !!user,
    refetchInterval: 30_000,
  });
  const alerts = unread?.unreadCount;

  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-bg pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <div className="mx-auto flex h-16 max-w-md items-stretch justify-around">
        {!user && (
          <>
            <TabItem to="/" end icon={Search} label="Explore" />
            <TabItem to="/login" icon={LogIn} label="Log in" />
          </>
        )}
        {role === 'CUSTOMER' && <CustomerTabs alerts={alerts} />}
        {role === 'RESTAURANT_OWNER' && <OwnerTabs alerts={alerts} />}
        {role === 'DELIVERY_PARTNER' && (
          <>
            <TabItem to="/rider" icon={Bike} label="Deliveries" />
            <TabItem to="/" end icon={Search} label="Explore" />
            <TabItem to="/notifications" icon={Bell} label="Alerts" badge={alerts} />
            <TabItem to="/account" icon={CircleUser} label="Account" />
          </>
        )}
        {role === 'ADMIN' && (
          <>
            <TabItem to="/admin" icon={ShieldCheck} label="Operations" />
            <TabItem to="/" end icon={Search} label="Explore" />
            <TabItem to="/notifications" icon={Bell} label="Alerts" badge={alerts} />
            <TabItem to="/account" icon={CircleUser} label="Account" />
          </>
        )}
      </div>
    </nav>
  );
}

function CustomerTabs({ alerts }: { alerts?: number }) {
  const { data: cart } = useCart();
  return (
    <>
      <TabItem to="/" end icon={Search} label="Explore" />
      <TabItem to="/orders" icon={ReceiptText} label="Orders" />
      <TabItem to="/cart" icon={ShoppingBag} label="Cart" badge={cart?.totalItemCount} />
      <TabItem to="/notifications" icon={Bell} label="Alerts" badge={alerts} />
      <TabItem to="/account" icon={CircleUser} label="Account" />
    </>
  );
}

function OwnerTabs({ alerts }: { alerts?: number }) {
  const { data } = useIncomingOrders();
  return (
    <>
      <TabItem to="/owner" icon={ChefHat} label="Kitchen" badge={data?.length} />
      <TabItem to="/" end icon={Search} label="Explore" />
      <TabItem to="/notifications" icon={Bell} label="Alerts" badge={alerts} />
      <TabItem to="/account" icon={CircleUser} label="Account" />
    </>
  );
}

/** Icon above label; the active tab turns brand colour (the Airbnb app pattern). */
function TabItem({ to, icon: Icon, label, badge, end }: { to: string; icon: typeof Bell; label: string; badge?: number; end?: boolean }) {
  const { pathname } = useLocation();
  // /owner/restaurants/… should still light up "Kitchen"
  const forced = !end && to !== '/' && pathname.startsWith(to);
  return (
    <NavLink
      to={to}
      end={end}
      viewTransition
      className={({ isActive }) =>
        cn(
          'pressable relative flex min-w-14 flex-1 flex-col items-center justify-center gap-1 text-[11px] font-semibold transition-[color,transform]',
          isActive || forced ? 'text-primary' : 'text-muted',
        )
      }
    >
      <span className="relative">
        <Icon size={22} strokeWidth={1.75} />
        {!!badge && <CountBadge n={badge} className="absolute -right-2.5 -top-1.5 h-4 min-w-4 border-2 border-bg px-1 text-[9px]" />}
      </span>
      {label}
    </NavLink>
  );
}

// ── Footer (DESIGN.md footer-light + legal-band) ─────────────

const FOOTER: { title: string; links: [string, string][] }[] = [
  { title: 'Order', links: [['Explore restaurants', '/'], ['Your orders', '/orders'], ['Cart', '/cart'], ['Notifications', '/notifications']] },
  { title: 'Partners', links: [['Add your restaurant', '/register'], ['Kitchen dashboard', '/owner'], ['Deliver with us', '/register'], ['Rider app', '/rider']] },
  { title: 'Account', links: [['Log in', '/login'], ['Sign up', '/register'], ['Forgot password', '/forgot-password'], ['Account settings', '/account']] },
];

function Footer() {
  return (
    <footer className="border-t border-line bg-surface-2">
      <div className="mx-auto grid w-full max-w-7xl gap-8 px-4 py-12 sm:grid-cols-3 sm:px-6 lg:px-10">
        {FOOTER.map((col) => (
          <div key={col.title}>
            <h3 className="mb-3 text-sm font-semibold">{col.title}</h3>
            <ul className="space-y-3">
              {col.links.map(([label, to]) => (
                <li key={label}>
                  <Link to={to} className="text-sm text-fg hover:underline">
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-line">
        <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-6 text-[13px] text-muted sm:px-6 lg:px-10">
          <span>© {new Date().getFullYear()} Food Delivery · A learning project</span>
          <span>Photos from Unsplash</span>
        </div>
      </div>
    </footer>
  );
}
