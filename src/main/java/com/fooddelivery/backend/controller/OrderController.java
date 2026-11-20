package com.fooddelivery.backend.controller;

import com.fooddelivery.backend.dto.request.CreateOrderRequest;
import com.fooddelivery.backend.dto.response.OrderResponse;
import com.fooddelivery.backend.entity.OrderStatus;
import com.fooddelivery.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Customer: Checkout active cart and create an order.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    /**
     * Customer: Fetch all their placed orders.
     */
    @GetMapping("/my")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.getCustomerOrders(page, size));
    }

    /**
     * Get specific order details (Authorized customer, restaurant owner, delivery partner, or admin).
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    /**
     * Restaurant Owner: Get orders received by restaurant.
     */
    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<Page<OrderResponse>> getRestaurantOrders(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getRestaurantOrders(restaurantId, status, page, size));
    }

    /**
     * Restaurant: Accept an order.
     */
    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<OrderResponse> acceptOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, OrderStatus.ACCEPTED, null));
    }

    /**
     * Restaurant: Mark order as preparing.
     */
    @PatchMapping("/{id}/preparing")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<OrderResponse> prepareOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, OrderStatus.PREPARING, null));
    }

    /**
     * Restaurant: Mark order as ready for pickup.
     */
    @PatchMapping("/{id}/ready")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<OrderResponse> readyOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, OrderStatus.READY, null));
    }

    /**
     * Delivery Partner: Mark order as picked up.
     */
    @PatchMapping("/{id}/pickup")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public ResponseEntity<OrderResponse> pickupOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, OrderStatus.PICKED_UP, null));
    }

    /**
     * Delivery Partner: Mark order as delivered.
     */
    @PatchMapping("/{id}/deliver")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public ResponseEntity<OrderResponse> deliverOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, OrderStatus.DELIVERED, null));
    }

    /**
     * Cancel an order (Customer or Restaurant).
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody com.fooddelivery.backend.dto.request.CancelOrderRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, OrderStatus.CANCELLED, request.getReason()));
    }
}

