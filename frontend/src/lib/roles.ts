import type { Role } from '@/api/types';

/** Where each kind of user "lives" after logging in. */
export const ROLE_HOME: Record<Role, string> = {
  CUSTOMER: '/',
  RESTAURANT_OWNER: '/owner',
  DELIVERY_PARTNER: '/rider',
  ADMIN: '/admin',
};

export const ROLE_LABEL: Record<Role, string> = {
  CUSTOMER: 'Customer',
  RESTAURANT_OWNER: 'Restaurant owner',
  DELIVERY_PARTNER: 'Delivery partner',
  ADMIN: 'Admin',
};
