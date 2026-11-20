package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByUserId(UUID userId);

    Optional<CartItem> findByUserIdAndMenuItemId(UUID userId, UUID menuItemId);

    void deleteByUserId(UUID userId);
}

