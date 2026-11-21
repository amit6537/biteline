/*
 * TypeScript copies of the backend's DTOs (dto/response, dto/request).
 * If a backend DTO changes, change it here too.
 *
 * Dates come as LocalDateTime strings without a timezone, e.g.
 * "2026-11-22T14:05:00" — see lib/format.ts.
 */

export type UUID = string;
export type DateTime = string;

export type Role = 'CUSTOMER' | 'RESTAURANT_OWNER' | 'DELIVERY_PARTNER' | 'ADMIN';

export type OrderStatus =
  | 'PLACED'
  | 'ACCEPTED'
  | 'PREPARING'
  | 'READY'
  | 'PICKED_UP'
  | 'DELIVERED'
  | 'CANCELLED';

export type PaymentMethod = 'UPI' | 'CARD' | 'CASH' | 'WALLET';
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';

export type NotificationType =
  | 'ORDER_PLACED'
  | 'NEW_ORDER'
  | 'PAYMENT_SUCCESS'
  | 'ORDER_STATUS'
  | 'ORDER_CANCELLED'
  | 'PARTNER_ASSIGNED'
  | 'NEW_DELIVERY_JOB'
  | 'REVIEW_REQUEST'
  | 'DELIVERY_DELAYED'
  | 'OPS_ALERT';

/** Spring's Page<T> as JSON (we only read these fields). */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages?: number;
  number?: number;
  size?: number;
}

// ── Auth ─────────────────────────────────────────────────────

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number; // seconds
  userId: UUID;
  name: string;
  email: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  phone: string;
  password: string;
  role?: Exclude<Role, 'ADMIN'>;
}

// ── Restaurants & menu ───────────────────────────────────────

export interface Restaurant {
  id: UUID;
  ownerId: UUID;
  name: string;
  description: string | null;
  cuisineType: string | null;
  imageUrl: string | null;
  avgRating: number | null;
  totalReviews: number | null;
  isOpen: boolean;
  deliveryRadiusKm: number | null;
  minOrderAmount: number | null;
  latitude: number | null;
  longitude: number | null;
  city: string;
  avgPrepMinutes: number | null;
}

export interface NearbyRestaurant {
  restaurant: Restaurant;
  distanceKm: number;
  estimatedDeliveryMinutes: number;
}

export interface MenuItem {
  id: UUID;
  restaurantId: UUID;
  name: string;
  description: string | null;
  price: number;
  category: string | null;
  imageUrl: string | null;
  isAvailable: boolean;
  isVegetarian: boolean;
}

export interface RatingSummary {
  restaurantId: UUID;
  avgRating: number | null;
  totalReviews: number;
  distribution: Record<'1' | '2' | '3' | '4' | '5', number>;
}

export interface Review {
  id: UUID;
  orderId: UUID;
  restaurantId: UUID;
  reviewerName: string;
  foodRating: number;
  deliveryRating: number | null;
  comment: string | null;
  createdAt: DateTime;
  updatedAt: DateTime;
  edited: boolean;
}

export interface ReviewRequest {
  foodRating: number;
  deliveryRating?: number;
  comment?: string;
}

// ── Cart ─────────────────────────────────────────────────────

export interface CartItem {
  id: UUID;
  menuItemId: UUID;
  menuItemName: string;
  price: number;
  quantity: number;
  itemTotal: number;
  isAvailable: boolean;
  imageUrl: string | null;
}

export interface Cart {
  restaurantId: UUID | null;
  restaurantName: string | null;
  items: CartItem[];
  subtotal: number;
  deliveryFee: number;
  totalAmount: number;
  totalItemCount: number;
}

// ── Addresses ────────────────────────────────────────────────

export interface Address {
  id: UUID;
  label: string;
  street: string;
  city: string;
  state: string;
  pincode: string;
  latitude: number | null;
  longitude: number | null;
  isDefault: boolean;
  formattedAddress: string;
  createdAt: DateTime;
}

export interface CreateAddressRequest {
  label?: string;
  street: string;
  city: string;
  state: string;
  pincode: string;
  latitude?: number;
  longitude?: number;
  isDefault?: boolean;
}

// ── Orders, payments, tracking ───────────────────────────────

export interface OrderItem {
  id: UUID;
  menuItemId: UUID;
  name: string;
  price: number;
  quantity: number;
  itemTotal: number;
}

export interface Order {
  id: UUID;
  customerId: UUID;
  customerName: string;
  restaurantId: UUID;
  restaurantName: string;
  deliveryAddressId: UUID;
  deliveryAddressText: string;
  status: OrderStatus;
  subtotal: number;
  deliveryFee: number;
  totalAmount: number;
  specialInstructions: string | null;
  items: OrderItem[];
  placedAt: DateTime;
  acceptedAt: DateTime | null;
  readyAt: DateTime | null;
  deliveredAt: DateTime | null;
  cancelledAt: DateTime | null;
  cancellationReason: string | null;
  estimatedDeliveryAt: DateTime | null;
}

export interface Payment {
  id: UUID;
  orderId: UUID;
  status: PaymentStatus;
  amount: number;
  method: PaymentMethod;
  transactionId: string | null;
  idempotencyKey: string;
  paidAt: DateTime | null;
  createdAt: DateTime;
}

export interface Tracking {
  orderId: UUID;
  status: OrderStatus;
  estimatedDeliveryAt: DateTime | null;
  etaMinutes: number | null;
  restaurantName: string;
  restaurantLatitude: number | null;
  restaurantLongitude: number | null;
  deliveryLatitude: number | null;
  deliveryLongitude: number | null;
  partnerName: string | null;
  partnerLatitude: number | null;
  partnerLongitude: number | null;
  partnerLastSeenAt: DateTime | null;
  partnerLocationStale: boolean | null;
  partnerDistanceKm: number | null;
}

// ── Notifications ────────────────────────────────────────────

export interface AppNotification {
  id: UUID;
  type: NotificationType;
  title: string;
  message: string;
  orderId: UUID | null;
  isRead: boolean;
  createdAt: DateTime;
  readAt: DateTime | null;
}

// ── Restaurant owner ─────────────────────────────────────────

export interface RestaurantRequest {
  name: string;
  description?: string;
  cuisineType?: string;
  imageUrl?: string;
  latitude: number;
  longitude: number;
  city: string;
  deliveryRadiusKm?: number;
  avgPrepMinutes?: number;
  minOrderAmount?: number;
}

/** PATCH: send only what changed. */
export type RestaurantUpdate = Partial<Omit<RestaurantRequest, 'city'>> & { isOpen?: boolean };

export interface MenuItemRequest {
  name: string;
  description?: string;
  price: number;
  category: string;
  imageUrl?: string;
  isVegetarian: boolean;
}

export type MenuItemUpdate = Partial<MenuItemRequest> & { isAvailable?: boolean };

// ── Delivery partner ─────────────────────────────────────────

export type AssignmentStatus = 'ASSIGNED' | 'PICKED_UP' | 'DELIVERED' | 'CANCELLED';

export interface DeliveryAssignment {
  id: UUID;
  orderId: UUID;
  orderStatus: OrderStatus;
  status: AssignmentStatus;
  partnerId: UUID;
  partnerName: string;
  restaurantName: string;
  restaurantLatitude: number | null;
  restaurantLongitude: number | null;
  customerName: string;
  deliveryAddressText: string;
  orderTotal: number;
  assignedAt: DateTime;
  pickedUpAt: DateTime | null;
  deliveredAt: DateTime | null;
}

export interface PartnerStatus {
  partnerId: UUID;
  isOnline: boolean;
  isAvailable: boolean;
  latitude: number | null;
  longitude: number | null;
  lastSeenAt: DateTime | null;
  currentAssignmentId: UUID | null;
}

export interface PartnerRating {
  partnerId: UUID;
  avgDeliveryRating: number | null;
  totalRatings: number;
}

// ── Admin ────────────────────────────────────────────────────

export interface StuckOrder {
  orderId: UUID;
  restaurantId: UUID;
  restaurantName: string;
  restaurantLatitude: number | null;
  restaurantLongitude: number | null;
  readyAt: DateTime | null;
  waitingMinutes: number;
  alerted: boolean;
}
