package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.Order;
import com.fooddelivery.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Customer order history
    Page<Order> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);

    // Restaurant incoming orders
    Page<Order> findByRestaurantIdOrderByPlacedAtDesc(UUID restaurantId, Pageable pageable);

    // Restaurant incoming orders filtered by status
    Page<Order> findByRestaurantIdAndStatusOrderByPlacedAtDesc(UUID restaurantId, OrderStatus status, Pageable pageable);

    // Specific order for customer
    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    // Specific order for restaurant owner
    Optional<Order> findByIdAndRestaurantOwnerId(UUID id, UUID ownerId);
}

