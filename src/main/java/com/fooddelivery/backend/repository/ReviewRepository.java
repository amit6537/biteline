package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByOrderId(UUID orderId);

    Page<Review> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    /** Lock a review while editing/deleting it, so two edits can't both use the same "old" rating. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") UUID id);

    /** How many 1★, 2★ ... 5★ reviews — for the rating bar chart. */
    @Query("SELECT r.foodRating AS stars, COUNT(r) AS count FROM Review r "
            + "WHERE r.restaurantId = :restaurantId GROUP BY r.foodRating")
    List<StarCount> countByStars(@Param("restaurantId") UUID restaurantId);

    /** A delivery partner's average delivery rating. */
    @Query("SELECT AVG(r.deliveryRating) AS average, COUNT(r.deliveryRating) AS count FROM Review r "
            + "WHERE r.partnerId = :partnerId AND r.deliveryRating IS NOT NULL")
    PartnerRating partnerRating(@Param("partnerId") UUID partnerId);

    // CONCEPT: Interface projection — Spring fills these getters from the
    // query's column aliases. No entity, no extra class to write.
    interface StarCount {
        Integer getStars();
        Long getCount();
    }

    interface PartnerRating {
        Double getAverage();
        Long getCount();
    }
}
