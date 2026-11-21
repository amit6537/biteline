import { request } from './client';
import type {
  Address,
  AppNotification,
  AuthResponse,
  Cart,
  DeliveryAssignment,
  MenuItemRequest,
  MenuItemUpdate,
  OrderStatus,
  PartnerRating,
  PartnerStatus,
  RestaurantRequest,
  RestaurantUpdate,
  StuckOrder,
  CreateAddressRequest,
  LoginRequest,
  MenuItem,
  NearbyRestaurant,
  Order,
  Page,
  Payment,
  PaymentMethod,
  RatingSummary,
  RegisterRequest,
  Restaurant,
  Review,
  ReviewRequest,
  Tracking,
  UUID,
} from './types';

/* One function per backend endpoint, grouped like the backend controllers. */

const q = (params: Record<string, string | number | boolean | undefined>) => {
  const s = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => v !== undefined && v !== '' && s.set(k, String(v)));
  const str = s.toString();
  return str ? `?${str}` : '';
};

export const authApi = {
  login: (body: LoginRequest) => request<AuthResponse>('POST', '/api/auth/login', { body, auth: false }),
  register: (body: RegisterRequest) => request<AuthResponse>('POST', '/api/auth/register', { body, auth: false }),
  logout: (refreshToken: string) =>
    request<void>('POST', '/api/auth/logout', { body: { refreshToken }, auth: false }),
  logoutAll: () => request<void>('POST', '/api/auth/logout-all', { body: {} }),
  /** Always the same answer, whether the email has an account or not. */
  forgotPassword: (email: string) =>
    request<{ message: string }>('POST', '/api/auth/forgot-password', { body: { email }, auth: false }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('POST', '/api/auth/reset-password', { body: { token, newPassword }, auth: false }),
};

export const restaurantApi = {
  byCity: (city: string, page = 0) =>
    request<Page<Restaurant>>('GET', `/api/restaurants${q({ city, page, size: 24 })}`, { auth: false }),
  search: (city: string, name: string) =>
    request<Page<Restaurant>>('GET', `/api/restaurants/search${q({ city, name, size: 24 })}`, { auth: false }),
  nearby: (lat: number, lng: number, radiusKm = 5) =>
    request<NearbyRestaurant[]>('GET', `/api/restaurants/nearby${q({ lat, lng, radiusKm })}`, { auth: false }),
  get: (id: UUID) => request<Restaurant>('GET', `/api/restaurants/${id}`, { auth: false }),
  menu: (id: UUID) => request<MenuItem[]>('GET', `/api/restaurants/${id}/menu`, { auth: false }),
  ratingSummary: (id: UUID) =>
    request<RatingSummary>('GET', `/api/restaurants/${id}/rating-summary`, { auth: false }),
  reviews: (id: UUID, page = 0) =>
    request<Page<Review>>('GET', `/api/restaurants/${id}/reviews${q({ page, size: 5 })}`, { auth: false }),
};

export const cartApi = {
  get: () => request<Cart>('GET', '/api/cart'),
  add: (menuItemId: UUID, quantity = 1) => request<Cart>('POST', '/api/cart/items', { body: { menuItemId, quantity } }),
  update: (itemId: UUID, quantity: number) =>
    request<Cart>('PATCH', `/api/cart/items/${itemId}`, { body: { quantity } }),
  remove: (itemId: UUID) => request<Cart>('DELETE', `/api/cart/items/${itemId}`),
  clear: () => request<void>('DELETE', '/api/cart'),
};

export const addressApi = {
  list: () => request<Address[]>('GET', '/api/addresses'),
  create: (body: CreateAddressRequest) => request<Address>('POST', '/api/addresses', { body }),
  remove: (id: UUID) => request<void>('DELETE', `/api/addresses/${id}`),
};

export const orderApi = {
  place: (deliveryAddressId: UUID, specialInstructions?: string) =>
    request<Order>('POST', '/api/orders', { body: { deliveryAddressId, specialInstructions } }),
  mine: (page = 0) => request<Page<Order>>('GET', `/api/orders/my${q({ page, size: 20 })}`),
  get: (id: UUID) => request<Order>('GET', `/api/orders/${id}`),
  tracking: (id: UUID) => request<Tracking>('GET', `/api/orders/${id}/tracking`),
  /** reason is required by the backend */
  cancel: (id: UUID, reason: string) => request<Order>('PATCH', `/api/orders/${id}/cancel`, { body: { reason } }),
  review: (orderId: UUID, body: ReviewRequest) =>
    request<Review>('POST', `/api/orders/${orderId}/review`, { body }),
};

export const paymentApi = {
  /** idempotencyKey: same key twice = the backend charges only once (safe to retry). */
  pay: (orderId: UUID, method: PaymentMethod, idempotencyKey: string) =>
    request<Payment>('POST', '/api/payments/process', { body: { orderId, method, idempotencyKey } }),
  forOrder: (orderId: UUID) => request<Payment>('GET', `/api/payments/order/${orderId}`),
};

export const notificationApi = {
  list: (page = 0) => request<Page<AppNotification>>('GET', `/api/notifications${q({ page, size: 30 })}`),
  unreadCount: () => request<{ unreadCount: number }>('GET', '/api/notifications/unread-count'),
  markRead: (id: UUID) => request<AppNotification>('PATCH', `/api/notifications/${id}/read`),
  markAllRead: () => request<{ updated: number }>('PATCH', '/api/notifications/read-all'),
};

/* ── Restaurant owner ── */
export const ownerApi = {
  myRestaurants: () => request<Restaurant[]>('GET', '/api/restaurants/my'),
  create: (body: RestaurantRequest) => request<Restaurant>('POST', '/api/restaurants', { body }),
  update: (id: UUID, body: RestaurantUpdate) => request<Restaurant>('PATCH', `/api/restaurants/${id}`, { body }),
  remove: (id: UUID) => request<void>('DELETE', `/api/restaurants/${id}`),
  /** Owner sees sold-out items too (the public menu endpoint is used, it returns all items). */
  menu: (id: UUID) => request<MenuItem[]>('GET', `/api/restaurants/${id}/menu`),
  addItem: (restaurantId: UUID, body: MenuItemRequest) =>
    request<MenuItem>('POST', `/api/restaurants/${restaurantId}/menu`, { body }),
  updateItem: (restaurantId: UUID, itemId: UUID, body: MenuItemUpdate) =>
    request<MenuItem>('PATCH', `/api/restaurants/${restaurantId}/menu/${itemId}`, { body }),
  removeItem: (restaurantId: UUID, itemId: UUID) =>
    request<void>('DELETE', `/api/restaurants/${restaurantId}/menu/${itemId}`),

  orders: (restaurantId: UUID, status?: OrderStatus, page = 0, size = 50) =>
    request<Page<Order>>('GET', `/api/orders/restaurant/${restaurantId}${q({ status, page, size })}`),
  accept: (orderId: UUID) => request<Order>('PATCH', `/api/orders/${orderId}/accept`),
  preparing: (orderId: UUID) => request<Order>('PATCH', `/api/orders/${orderId}/preparing`),
  ready: (orderId: UUID) => request<Order>('PATCH', `/api/orders/${orderId}/ready`),
};

/* ── Delivery partner ── */
export const deliveryApi = {
  status: () => request<PartnerStatus>('GET', '/api/delivery/status'),
  /** available: true = go online, false = go offline, undefined = only update location */
  updateStatus: (latitude: number, longitude: number, available?: boolean) =>
    request<PartnerStatus>('PUT', '/api/delivery/status', { body: { latitude, longitude, available } }),
  /** undefined (HTTP 204) = no job right now */
  current: () => request<DeliveryAssignment | undefined>('GET', '/api/delivery/assignments/current'),
  history: (page = 0) => request<Page<DeliveryAssignment>>('GET', `/api/delivery/assignments${q({ page, size: 20 })}`),
  reject: (assignmentId: UUID) =>
    request<DeliveryAssignment | undefined>('POST', `/api/delivery/assignments/${assignmentId}/reject`),
  rating: () => request<PartnerRating>('GET', '/api/delivery/rating'),
  pickup: (orderId: UUID) => request<Order>('PATCH', `/api/orders/${orderId}/pickup`),
  deliver: (orderId: UUID) => request<Order>('PATCH', `/api/orders/${orderId}/deliver`),
};

/* ── Admin ── */
export const adminApi = {
  unassigned: () => request<StuckOrder[]>('GET', '/api/admin/orders/unassigned'),
  assign: (orderId: UUID, partnerId: UUID) =>
    request<DeliveryAssignment>('POST', `/api/admin/orders/${orderId}/assign`, { body: { partnerId } }),
};
