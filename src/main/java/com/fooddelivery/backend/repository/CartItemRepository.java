package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.CartItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    /**
     * Loads the user's cart rows AND locks them. Used at checkout so a
     * double-clicked "Place order" can't turn one cart into two orders:
     * the second request waits, then finds the cart already emptied.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CartItem c WHERE c.user.id = :userId")
    List<CartItem> findByUserIdForUpdate(@Param("userId") UUID userId);

    List<CartItem> findByUserId(UUID userId);

    Optional<CartItem> findByUserIdAndMenuItemId(UUID userId, UUID menuItemId);

    void deleteByUserId(UUID userId);
}

