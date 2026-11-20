package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    // All items for a restaurant (the full menu)
    List<MenuItem> findByRestaurantIdOrderByCategory(UUID restaurantId);

    // Available items only — for ordering
    List<MenuItem> findByRestaurantIdAndIsAvailableTrue(UUID restaurantId);

    // All items in a category for a restaurant
    List<MenuItem> findByRestaurantIdAndCategory(UUID restaurantId, String category);

    // Check if an item belongs to a specific restaurant (for auth/validation)
    Optional<MenuItem> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    // Check ownership chain: item → restaurant → owner
    // Used to verify: "does this item belong to a restaurant owned by this user?"
    Optional<MenuItem> findByIdAndRestaurantOwnerId(UUID id, UUID ownerId);
}

