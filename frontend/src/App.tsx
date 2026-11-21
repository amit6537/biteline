import { lazy } from 'react';
import { BrowserRouter, Link, Route, Routes } from 'react-router';
import { Compass } from 'lucide-react';
import { RequireAuth } from '@/auth/RequireAuth';
import { AppShell } from '@/components/AppShell';
import { Button, EmptyState } from '@/components/ui';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage } from '@/pages/AuthPages';
import { HomePage } from '@/pages/HomePage';
import { RestaurantPage } from '@/pages/RestaurantPage';

/*
 * Code splitting: only the pages EVERY visitor needs (browse, restaurant,
 * log in) are in the first download. The rest is fetched the first time
 * someone opens it — a customer never downloads the kitchen dashboard.
 * <Suspense> in AppShell shows a skeleton while a page chunk loads.
 */
const page = <T,>(load: () => Promise<T>, pick: (m: T) => React.ComponentType) =>
  lazy(() => load().then((m) => ({ default: pick(m) })));

const CartPage = page(() => import('@/pages/CartPage'), (m) => m.CartPage);
const OrdersPage = page(() => import('@/pages/OrdersPage'), (m) => m.OrdersPage);
const OrderDetailPage = page(() => import('@/pages/OrderDetailPage'), (m) => m.OrderDetailPage);
const NotificationsPage = page(() => import('@/pages/NotificationsPage'), (m) => m.NotificationsPage);
const AccountPage = page(() => import('@/pages/AccountPage'), (m) => m.AccountPage);
const OwnerDashboard = page(() => import('@/pages/owner/OwnerDashboard'), (m) => m.OwnerDashboard);
const NewRestaurantPage = page(() => import('@/pages/owner/ManageRestaurantPage'), (m) => m.NewRestaurantPage);
const ManageRestaurantPage = page(() => import('@/pages/owner/ManageRestaurantPage'), (m) => m.ManageRestaurantPage);
const RiderDashboard = page(() => import('@/pages/rider/RiderDashboard'), (m) => m.RiderDashboard);
const AdminPage = page(() => import('@/pages/admin/AdminPage'), (m) => m.AdminPage);

/*
 * Routes
 *   public:    /  /restaurants/:id  /login  /register  /forgot-password  /reset-password?token=
 *   logged in: /orders  /orders/:id  /notifications  /account
 *   customer:  /cart
 *   owner:     /owner  /owner/restaurants/new  /owner/restaurants/:id
 *   rider:     /rider
 *   admin:     /admin
 */
export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<HomePage />} />
          <Route path="restaurants/:id" element={<RestaurantPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />
          <Route path="reset-password" element={<ResetPasswordPage />} />

          <Route path="cart" element={<RequireAuth roles={['CUSTOMER']}><CartPage /></RequireAuth>} />
          <Route path="orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
          <Route path="orders/:id" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
          <Route path="notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
          <Route path="account" element={<RequireAuth><AccountPage /></RequireAuth>} />

          <Route path="owner" element={<RequireAuth roles={['RESTAURANT_OWNER']}><OwnerDashboard /></RequireAuth>} />
          <Route path="owner/restaurants/new" element={<RequireAuth roles={['RESTAURANT_OWNER']}><NewRestaurantPage /></RequireAuth>} />
          <Route path="owner/restaurants/:id" element={<RequireAuth roles={['RESTAURANT_OWNER']}><ManageRestaurantPage /></RequireAuth>} />
          <Route path="rider" element={<RequireAuth roles={['DELIVERY_PARTNER']}><RiderDashboard /></RequireAuth>} />
          <Route path="admin" element={<RequireAuth roles={['ADMIN']}><AdminPage /></RequireAuth>} />

          <Route
            path="*"
            element={
              <EmptyState
                icon={Compass}
                title="Page not found"
                text="That link doesn't lead anywhere."
                action={<Link to="/"><Button variant="secondary">Go home</Button></Link>}
              />
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
