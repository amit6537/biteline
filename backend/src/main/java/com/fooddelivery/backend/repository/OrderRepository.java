package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.Order;
import com.fooddelivery.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Loads an order AND locks its row (SELECT ... FOR UPDATE) until the
     * transaction ends. A second request touching the same order waits
     * instead of racing — e.g. "accept" and "cancel" clicked at the same
     * moment, or two payments for one order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

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

