package com.fooddelivery.backend.service;

import com.fooddelivery.backend.dto.request.CreateOrderRequest;
import com.fooddelivery.backend.dto.response.OrderItemResponse;
import com.fooddelivery.backend.dto.response.OrderResponse;
import com.fooddelivery.backend.entity.*;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.exception.ForbiddenException;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.kafka.OrderEventProducer;
import com.fooddelivery.backend.kafka.OrderEvents;
import com.fooddelivery.backend.repository.CartItemRepository;
import com.fooddelivery.backend.repository.OrderRepository;
import com.fooddelivery.backend.repository.PaymentRepository;
import com.fooddelivery.backend.repository.RestaurantRepository;
import com.fooddelivery.backend.util.SecurityUtils;
import com.fooddelivery.backend.util.PageUtils;
import com.fooddelivery.backend.util.GeoUtils;
import com.fooddelivery.backend.dto.response.TrackingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OrderService — Manages checkout, order creation, and order history.
 *
 * CONCEPT: ACID Transactions in Order Creation
 * ─────────────────────────────────────────────
 * createOrder is annotated with @Transactional:
 * 1. Validates that the cart is not empty.
 * 2. Validates restaurant is still OPEN and menu items are still AVAILABLE.
 * 3. Enforces minimum order amount check.
 * 4. Freezes price and item name into order_items (historical price snapshot).
 * 5. Freezes delivery address text (defensive snapshot).
 * 6. Empties the user's cart.
 * If ANY step fails, everything is rolled back automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressService addressService;
    private final SecurityUtils securityUtils;
    private final com.fooddelivery.backend.statemachine.OrderStateMachine orderStateMachine;
    private final OrderEventProducer eventProducer;
    private final PaymentRepository paymentRepository;
    private final DeliveryService deliveryService;
    private final EtaService etaService;

    private static final BigDecimal DEFAULT_DELIVERY_FEE = new BigDecimal("30.00");

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        User customer = securityUtils.getCurrentUser();

        // 1. Fetch AND lock the user's cart rows. If "Place order" is
        //    double-clicked, the second request waits here, then sees an
        //    empty cart — so one cart can never become two orders.
        List<CartItem> cartItems = cartItemRepository.findByUserIdForUpdate(customer.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Your cart is empty. Add items before placing an order.");
        }

        // 2. Validate restaurant
        Restaurant restaurant = cartItems.get(0).getMenuItem().getRestaurant();
        if (!restaurant.isOpen()) {
            throw new BadRequestException("Restaurant '" + restaurant.getName() + "' is currently closed.");
        }

        // 3. Validate Address belongs to user
        Address address = addressService.getAddressEntityByIdAndUser(request.getDeliveryAddressId(), customer.getId());

        // 3b. Phase 12: does this restaurant deliver that far?
        //     (Addresses saved without coordinates can't be checked — they're
        //     accepted, and the ETA uses a default travel time.)
        if (address.getLatitude() != null && address.getLongitude() != null) {
            double km = GeoUtils.distanceKm(restaurant.getLatitude(), restaurant.getLongitude(),
                    address.getLatitude(), address.getLongitude());
            if (km > restaurant.getDeliveryRadiusKm()) {
                throw new BadRequestException(String.format(
                        "'%s' delivers within %.1f km, but this address is %.1f km away.",
                        restaurant.getName(), restaurant.getDeliveryRadiusKm(), km));
            }
        }

        // 4. Validate items availability and build line items with price snapshot
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        Order order = Order.builder()
                .customer(customer)
                .restaurant(restaurant)
                .deliveryAddress(address)
                .deliveryAddressText(address.toFormattedAddress())
                .deliveryLat(address.getLatitude())   // snapshot, like the address text
                .deliveryLng(address.getLongitude())
                .status(OrderStatus.PLACED)
                .deliveryFee(DEFAULT_DELIVERY_FEE)
                .specialInstructions(request.getSpecialInstructions())
                .build();

        for (CartItem ci : cartItems) {
            MenuItem mi = ci.getMenuItem();
            if (!mi.isAvailable()) {
                throw new BadRequestException("Item '" + mi.getName() + "' is no longer available.");
            }

            BigDecimal itemTotal = mi.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            subtotal = subtotal.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItem(mi)
                    .nameAtTime(mi.getName())
                    .priceAtTime(mi.getPrice())
                    .quantity(ci.getQuantity())
                    .itemTotal(itemTotal)
                    .build();

            orderItems.add(orderItem);
        }

        // 5. Minimum order amount check
        if (restaurant.getMinOrderAmount() != null && subtotal.compareTo(BigDecimal.valueOf(restaurant.getMinOrderAmount())) < 0) {
            throw new BadRequestException(String.format("Minimum order amount for %s is ₹%.2f. Current subtotal: ₹%.2f",
                    restaurant.getName(), restaurant.getMinOrderAmount(), subtotal));
        }

        BigDecimal totalAmount = subtotal.add(DEFAULT_DELIVERY_FEE);
        order.setSubtotal(subtotal);
        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);

        // Phase 12: real ETA (prep time + partner search + travel) instead of a fixed 40 min
        etaService.refresh(order);

        // 6. Save order (cascades order_items)
        Order savedOrder = orderRepository.save(order);

        // 7. Clear cart atomically within the same transaction
        cartItemRepository.deleteByUserId(customer.getId());

        log.info("Order successfully created: id={}, customer={}, restaurant={}, total={}",
                savedOrder.getId(), customer.getEmail(), restaurant.getName(), totalAmount);

        // 8. Publish OrderPlacedEvent to Kafka (async, after DB commit)
        eventProducer.publishOrderPlaced(new OrderEvents.OrderPlacedEvent(
                savedOrder.getId(),
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                restaurant.getId(),
                restaurant.getName(),
                totalAmount,
                savedOrder.getPlacedAt()
        ));

        return toOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(int page, int size) {
        UUID customerId = securityUtils.getCurrentUserId();
        Pageable pageable = PageUtils.of(page, size);
        return orderRepository.findByCustomerIdOrderByPlacedAtDesc(customerId, pageable)
                .map(this::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        return toOrderResponse(loadViewableOrder(orderId, securityUtils.getCurrentUser()));
    }

    /**
     * Phase 12: live tracking — status, ETA, and where the rider is.
     * Same "who may see this order" rules as getOrderById.
     * The rider's location is shown ONLY while they are on this job
     * (ASSIGNED / PICKED_UP) — never after delivery (their privacy).
     */
    @Transactional(readOnly = true)
    public TrackingResponse getTracking(UUID orderId) {
        Order order = loadViewableOrder(orderId, securityUtils.getCurrentUser());
        Restaurant r = order.getRestaurant();
        LocalDateTime now = LocalDateTime.now();

        TrackingResponse.TrackingResponseBuilder t = TrackingResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .estimatedDeliveryAt(order.getEstimatedDeliveryAt())
                .etaMinutes(order.getEstimatedDeliveryAt() == null
                        || order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED
                        ? null : Math.max(0, java.time.Duration.between(now, order.getEstimatedDeliveryAt()).toMinutes()))
                .restaurantName(r.getName())
                .restaurantLatitude(r.getLatitude())
                .restaurantLongitude(r.getLongitude())
                .deliveryLatitude(order.getDeliveryLat())
                .deliveryLongitude(order.getDeliveryLng());

        deliveryService.activeAssignment(order.getId()).ifPresent(a -> {
            t.partnerName(a.getPartner().getName());
            deliveryService.partnerStatus(a.getPartner().getId()).ifPresent(s -> {
                boolean stale = etaService.isStale(s);
                t.partnerLatitude(s.getCurrentLat())
                        .partnerLongitude(s.getCurrentLng())
                        .partnerLastSeenAt(s.getLastSeenAt())
                        .partnerLocationStale(stale);
                if (s.getCurrentLat() != null && s.getCurrentLng() != null) {
                    double km;
                    if (order.getStatus() == OrderStatus.PICKED_UP && order.getDeliveryLat() != null) {
                        km = GeoUtils.distanceKm(s.getCurrentLat(), s.getCurrentLng(),
                                order.getDeliveryLat(), order.getDeliveryLng());
                    } else {
                        km = GeoUtils.distanceKm(s.getCurrentLat(), s.getCurrentLng(), r.getLatitude(), r.getLongitude());
                    }
                    t.partnerDistanceKm(GeoUtils.round2(km));
                }
            });
        });
        return t.build();
    }

    /** Load an order and check the current user may see it (else 403). */
    private Order loadViewableOrder(UUID orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Who may see this order: its customer, the restaurant owner, an admin,
        // or a delivery partner who was ASSIGNED to it (Phase 8). Before, every
        // partner could read every customer's address.
        boolean isCustomer = order.getCustomer().getId().equals(user.getId());
        boolean isOwner = order.getRestaurant().getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isAssignedPartner = user.getRole() == Role.DELIVERY_PARTNER
                && deliveryService.wasAssignedTo(order.getId(), user.getId());

        if (!isCustomer && !isOwner && !isAdmin && !isAssignedPartner) {
            throw new ForbiddenException("You are not authorized to view this order.");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getRestaurantOrders(UUID restaurantId, OrderStatus status, int page, int size) {
        User user = securityUtils.getCurrentUser();
        Pageable pageable = PageUtils.of(page, size);

        // Verify restaurant ownership (admins bypass)
        if (user.getRole() != Role.ADMIN) {
            restaurantRepository.findByIdAndOwnerId(restaurantId, user.getId())
                    .orElseThrow(() -> new ForbiddenException("You do not own this restaurant: " + restaurantId));
        }

        if (status != null) {
            return orderRepository.findByRestaurantIdAndStatusOrderByPlacedAtDesc(restaurantId, status, pageable)
                    .map(this::toOrderResponse);
        } else {
            return orderRepository.findByRestaurantIdOrderByPlacedAtDesc(restaurantId, pageable)
                    .map(this::toOrderResponse);
        }
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus targetStatus, String reason) {
        User user = securityUtils.getCurrentUser();
        // Lock the order row: two transitions on the same order (e.g. owner
        // clicks "accept" while customer clicks "cancel") now run one after
        // the other, and the second one sees the first one's result.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Remember where we came from BEFORE changing anything (for the event)
        OrderStatus previousStatus = order.getStatus();

        // State Machine validation
        orderStateMachine.validateTransition(previousStatus, targetStatus);

        // Role-based authorization for specific transitions
        boolean isOwner = order.getRestaurant().getOwner().getId().equals(user.getId());
        boolean isCustomer = order.getCustomer().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        switch (targetStatus) {
            case ACCEPTED:
            case PREPARING:
            case READY:
                if (!isOwner && !isAdmin) {
                    throw new ForbiddenException("Only the restaurant owner can accept or update preparation status.");
                }
                break;
            case PICKED_UP:
            case DELIVERED:
                // Phase 8: not just "any partner" — must be THE assigned one
                if (!isAdmin) {
                    deliveryService.requireAssignedPartner(order, user);
                    // Phase 12 geofence: you must actually BE at the restaurant
                    // to pick up, and at the customer to deliver
                    deliveryService.checkGeofence(order, user.getId(), targetStatus == OrderStatus.PICKED_UP);
                }
                break;
            case CANCELLED:
                if (!isCustomer && !isOwner && !isAdmin) {
                    throw new ForbiddenException("You are not authorized to cancel this order.");
                }
                break;
            default:
                break;
        }

        // Apply transition timestamps
        LocalDateTime now = LocalDateTime.now();
        UUID deliveredBy = null; // the partner who delivered (set below)
        order.setStatus(targetStatus);

        switch (targetStatus) {
            case ACCEPTED -> order.setAcceptedAt(now);
            case READY -> order.setReadyAt(now);
            case PICKED_UP -> deliveryService.markPickedUp(order);
            case DELIVERED -> {
                order.setDeliveredAt(now);
                deliveredBy = deliveryService.markDelivered(order).orElse(user.getId());
            }
            case CANCELLED -> {
                order.setCancelledAt(now);
                order.setCancellationReason(reason != null ? reason : "Cancelled by user");
                // If the customer already paid, mark the payment REFUNDED so
                // money isn't kept for food that will never arrive.
                // (Mock gateway: a real one would call the refund API here.)
                paymentRepository.findByOrderId(order.getId())
                        .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                        .ifPresent(p -> {
                            p.setStatus(PaymentStatus.REFUNDED);
                            log.info("Payment {} refunded for cancelled order {}", p.getId(), order.getId());
                        });
            }
            default -> {}
        }

        etaService.refresh(order); // Phase 12: every step changes the ETA
        Order saved = orderRepository.save(order);
        log.info("Order status updated: orderId={}, {} -> {}", saved.getId(), previousStatus, saved.getStatus());

        // Publish status-changed event (consumed by NotificationService).
        // BUG FIXED: previousStatus used to be computed AFTER setStatus(),
        // so it always equalled the new status.
        eventProducer.publishOrderStatusChanged(new OrderEvents.OrderStatusChangedEvent(
                saved.getId(),
                saved.getCustomer().getId(),
                saved.getCustomer().getEmail(),
                saved.getRestaurant().getId(),
                saved.getRestaurant().getName(),
                previousStatus,
                targetStatus,
                LocalDateTime.now()
        ));

        // If order is READY → also publish order.ready for delivery assignment
        if (targetStatus == OrderStatus.READY) {
            eventProducer.publishOrderReady(new OrderEvents.OrderReadyEvent(
                    saved.getId(),
                    saved.getRestaurant().getId(),
                    saved.getRestaurant().getName(),
                    saved.getRestaurant().getLatitude(),
                    saved.getRestaurant().getLongitude(),
                    saved.getTotalAmount(),
                    LocalDateTime.now()
            ));
        }

        // If order is DELIVERED → publish order.delivered
        if (targetStatus == OrderStatus.DELIVERED) {
            eventProducer.publishOrderDelivered(new OrderEvents.OrderDeliveredEvent(
                    saved.getId(),
                    saved.getCustomer().getId(),
                    saved.getCustomer().getEmail(),
                    deliveredBy,
                    LocalDateTime.now()
            ));
        }

        return toOrderResponse(saved);
    }

    public OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream().map(item ->
                OrderItemResponse.builder()
                        .id(item.getId())
                        .menuItemId(item.getMenuItem().getId())
                        .name(item.getNameAtTime())
                        .price(item.getPriceAtTime())
                        .quantity(item.getQuantity())
                        .itemTotal(item.getItemTotal())
                        .build()
        ).collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer().getId())
                .customerName(order.getCustomer().getName())
                .restaurantId(order.getRestaurant().getId())
                .restaurantName(order.getRestaurant().getName())
                .deliveryAddressId(order.getDeliveryAddress().getId())
                .deliveryAddressText(order.getDeliveryAddressText())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .totalAmount(order.getTotalAmount())
                .specialInstructions(order.getSpecialInstructions())
                .items(itemResponses)
                .placedAt(order.getPlacedAt())
                .acceptedAt(order.getAcceptedAt())
                .readyAt(order.getReadyAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .cancellationReason(order.getCancellationReason())
                .estimatedDeliveryAt(order.getEstimatedDeliveryAt())
                .build();
    }
}

