package com.fooddelivery.backend.service;

import com.fooddelivery.backend.dto.request.AddToCartRequest;
import com.fooddelivery.backend.dto.request.UpdateCartItemRequest;
import com.fooddelivery.backend.dto.response.CartItemResponse;
import com.fooddelivery.backend.dto.response.CartResponse;
import com.fooddelivery.backend.entity.CartItem;
import com.fooddelivery.backend.entity.MenuItem;
import com.fooddelivery.backend.entity.Restaurant;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.exception.ConflictException;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.repository.CartItemRepository;
import com.fooddelivery.backend.repository.MenuItemRepository;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CartService — Manages the user's active shopping cart.
 *
 * CONCEPT: Single Restaurant Cart Invariant
 * ──────────────────────────────────────────
 * In food delivery platforms (Swiggy/Zomato), an active cart can only contain items
 * from a SINGLE restaurant at a time. If the customer attempts to add an item from
 * Restaurant B while their cart contains items from Restaurant A, we throw ConflictException.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final SecurityUtils securityUtils;

    private static final BigDecimal DEFAULT_DELIVERY_FEE = new BigDecimal("30.00");

    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        User user = securityUtils.getCurrentUser();

        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + request.getMenuItemId()));

        if (!menuItem.isAvailable()) {
            throw new BadRequestException("Menu item is currently unavailable: " + menuItem.getName());
        }

        Restaurant itemRestaurant = menuItem.getRestaurant();
        if (!itemRestaurant.isOpen()) {
            throw new BadRequestException("Restaurant '" + itemRestaurant.getName() + "' is currently closed");
        }

        List<CartItem> currentCart = cartItemRepository.findByUserId(user.getId());

        // Validate single restaurant rule
        if (!currentCart.isEmpty()) {
            Restaurant currentRestaurant = currentCart.get(0).getMenuItem().getRestaurant();
            if (!currentRestaurant.getId().equals(itemRestaurant.getId())) {
                throw new ConflictException("Your cart contains items from '" + currentRestaurant.getName() +
                        "'. Clear your cart before adding items from another restaurant.");
            }
        }

        // Check if item already in cart
        Optional<CartItem> existingItemOpt = cartItemRepository.findByUserIdAndMenuItemId(user.getId(), menuItem.getId());
        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            cartItemRepository.save(existing);
        } else {
            CartItem cartItem = CartItem.builder()
                    .user(user)
                    .menuItem(menuItem)
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(cartItem);
        }

        return getCart();
    }

    @Transactional(readOnly = true)
    public CartResponse getCart() {
        UUID userId = securityUtils.getCurrentUserId();
        List<CartItem> items = cartItemRepository.findByUserId(userId);

        if (items.isEmpty()) {
            return CartResponse.builder()
                    .items(Collections.emptyList())
                    .subtotal(BigDecimal.ZERO)
                    .deliveryFee(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.ZERO)
                    .totalItemCount(0)
                    .build();
        }

        Restaurant restaurant = items.get(0).getMenuItem().getRestaurant();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItemCount = 0;

        List<CartItemResponse> itemResponses = items.stream().map(ci -> {
            MenuItem mi = ci.getMenuItem();
            BigDecimal itemTotal = mi.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            return CartItemResponse.builder()
                    .id(ci.getId())
                    .menuItemId(mi.getId())
                    .menuItemName(mi.getName())
                    .price(mi.getPrice())
                    .quantity(ci.getQuantity())
                    .itemTotal(itemTotal)
                    .isAvailable(mi.isAvailable())
                    .imageUrl(mi.getImageUrl())
                    .build();
        }).collect(Collectors.toList());

        for (CartItemResponse cir : itemResponses) {
            subtotal = subtotal.add(cir.getItemTotal());
            totalItemCount += cir.getQuantity();
        }

        BigDecimal total = subtotal.add(DEFAULT_DELIVERY_FEE);

        return CartResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .items(itemResponses)
                .subtotal(subtotal)
                .deliveryFee(DEFAULT_DELIVERY_FEE)
                .totalAmount(total)
                .totalItemCount(totalItemCount)
                .build();
    }

    @Transactional
    public CartResponse updateCartItem(UUID cartItemId, UpdateCartItemRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));

        if (!cartItem.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Cart item not found for user: " + cartItemId);
        }

        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);
        return getCart();
    }

    @Transactional
    public CartResponse removeCartItem(UUID cartItemId) {
        UUID userId = securityUtils.getCurrentUserId();
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));

        if (!cartItem.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Cart item not found for user: " + cartItemId);
        }

        cartItemRepository.delete(cartItem);
        return getCart();
    }

    @Transactional
    public void clearCart() {
        UUID userId = securityUtils.getCurrentUserId();
        cartItemRepository.deleteByUserId(userId);
    }
}

