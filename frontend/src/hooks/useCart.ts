import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cartApi } from '@/api/endpoints';
import type { Cart, UUID } from '@/api/types';
import { useAuth } from '@/auth/AuthContext';

/*
 * One cart for the whole app (TanStack Query caches it under ['cart']).
 * Every change returns the full new cart from the backend, which we put
 * straight into the cache → the nav badge, menu page and cart page all
 * update together without another GET.
 */

export const CART_KEY = ['cart'] as const;

export function useCart() {
  const { user } = useAuth();
  const enabled = user?.role === 'CUSTOMER';
  return useQuery({ queryKey: CART_KEY, queryFn: cartApi.get, enabled, staleTime: 30_000 });
}

export function useCartActions() {
  const qc = useQueryClient();
  const put = (cart: Cart) => qc.setQueryData(CART_KEY, cart);
  const empty = () => qc.invalidateQueries({ queryKey: CART_KEY });

  const add = useMutation({
    mutationFn: ({ menuItemId, quantity = 1 }: { menuItemId: UUID; quantity?: number }) =>
      cartApi.add(menuItemId, quantity),
    onSuccess: put,
  });

  const setQty = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: UUID; quantity: number }) =>
      quantity <= 0 ? cartApi.remove(itemId) : cartApi.update(itemId, quantity),
    onSuccess: put,
  });

  const clear = useMutation({ mutationFn: cartApi.clear, onSuccess: empty });

  return { add, setQty, clear };
}

/** How many of this menu item are in the cart (0 if none). */
export function cartQtyOf(cart: Cart | undefined, menuItemId: UUID) {
  return cart?.items.find((i) => i.menuItemId === menuItemId);
}
