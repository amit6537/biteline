package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RestaurantRepository
 *
 * CONCEPT: Pagination with Spring Data JPA
 * ──────────────────────────────────────────
 * findByCity returns a Page<Restaurant>, not a List<Restaurant>.
 * WHY? A city like Mumbai could have 10,000 restaurants.
 * Loading all into memory would crash the app.
 *
 * Pageable tells the query: "give me page 0, 20 items per page."
 * Page<T> response includes: content list + total pages + total elements.
 * The client can then ask for page 1, 2, etc. — classic pagination.
 *
 * CONCEPT: @Query with JPQL
 * ──────────────────────────
 * Some queries can't be expressed with method naming conventions.
 * @Query lets us write JPQL (Java Persistence Query Language) —
 * SQL-like but operates on ENTITIES, not tables.
 *   "FROM Restaurant r" (entity name, not table name)
 *   "r.isOpen = true" (field name, not column name)
 *
 * Spring Data translates JPQL → SQL for the target database.
 * This keeps queries portable across DB vendors.
 */
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    // Paginated list of open restaurants in a city
    Page<Restaurant> findByCityIgnoreCaseAndIsOpenTrue(String city, Pageable pageable);

    // All restaurants in a city (open or closed) — for search
    Page<Restaurant> findByCityIgnoreCase(String city, Pageable pageable);

    /**
     * Atomic rating update — the core of "read-optimized aggregation".
     *
     *   new review   → applyRatingChange(id, +rating,      +1)
     *   edited review→ applyRatingChange(id, new - old,     0)
     *   deleted      → applyRatingChange(id, -rating,      -1)
     *
     * ONE UPDATE statement: Postgres reads and writes the row under its own
     * lock, so 100 reviews arriving at once still add up exactly. (Doing
     * "load restaurant → compute in Java → save" would lose updates.)
     * On the right side of SET, columns mean their OLD values.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = """
            UPDATE restaurants
            SET rating_sum    = rating_sum + :sumDelta,
                total_reviews = total_reviews + :countDelta,
                avg_rating    = CASE WHEN total_reviews + :countDelta <= 0 THEN 0
                                     ELSE ROUND((rating_sum + :sumDelta)::numeric / (total_reviews + :countDelta), 2)
                                END,
                updated_at    = NOW()
            WHERE id = :id
            """)
    int applyRatingChange(@Param("id") UUID id,
                          @Param("sumDelta") long sumDelta,
                          @Param("countDelta") int countDelta);

    /**
     * Phase 12: restaurants that can deliver to (lat, lng), nearest first.
     *
     * CONCEPT: Bounding box + exact distance (two-step geo filter)
     *   Step 1 — cheap: keep rows inside a lat/lng RECTANGLE around the user.
     *            Plain BETWEEN comparisons → can use idx_restaurants_lat_lng.
     *   Step 2 — exact: Haversine only on those few rows.
     *   Without step 1, Postgres would run acos/cos on EVERY restaurant in
     *   the country for every search.
     *
     * A restaurant shows up only if BOTH are true:
     *   distance ≤ what the user asked for (:radiusKm)
     *   distance ≤ that restaurant's own delivery_radius_km
     * (no point showing a place that won't deliver to you).
     *
     * :useLngBox = false near the poles / the ±180° line, where a longitude
     * rectangle stops making sense — then only the latitude band is used.
     */
    @Query(nativeQuery = true, value = """
            SELECT id, distance_km AS distanceKm FROM (
                SELECT r.id, r.delivery_radius_km,
                       6371 * acos(LEAST(1.0, GREATEST(-1.0,
                           cos(radians(:lat)) * cos(radians(r.latitude)) * cos(radians(r.longitude) - radians(:lng))
                         + sin(radians(:lat)) * sin(radians(r.latitude))))) AS distance_km
                FROM restaurants r
                WHERE r.latitude BETWEEN :minLat AND :maxLat
                  AND (:useLngBox = FALSE OR r.longitude BETWEEN :minLng AND :maxLng)
                  AND (:openOnly = FALSE OR r.is_open = TRUE)
            ) candidates
            WHERE distance_km <= :radiusKm
              AND distance_km <= delivery_radius_km
            ORDER BY distance_km
            LIMIT :limit
            """)
    List<NearbyRow> findNearby(@Param("lat") double lat, @Param("lng") double lng,
                               @Param("radiusKm") double radiusKm,
                               @Param("minLat") double minLat, @Param("maxLat") double maxLat,
                               @Param("minLng") double minLng, @Param("maxLng") double maxLng,
                               @Param("useLngBox") boolean useLngBox,
                               @Param("openOnly") boolean openOnly,
                               @Param("limit") int limit);

    /** Interface projection for findNearby: just id + distance. */
    interface NearbyRow {
        UUID getId();
        Double getDistanceKm();
    }

    // Just the owner's id — used by notifications (no lazy-loading needed)
    @Query("SELECT r.owner.id FROM Restaurant r WHERE r.id = :id")
    Optional<UUID> findOwnerIdById(@Param("id") UUID id);

    // All restaurants owned by a specific user
    List<Restaurant> findByOwnerId(UUID ownerId);

    // Find one restaurant belonging to a specific owner (for auth checks)
    Optional<Restaurant> findByIdAndOwnerId(UUID id, UUID ownerId);

    // Full-text search by name within a city (case-insensitive LIKE)
    @Query("""
            SELECT r FROM Restaurant r
            WHERE LOWER(r.city) = LOWER(:city)
            AND LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))
            AND (:openOnly = false OR r.isOpen = true)
            """)
    Page<Restaurant> searchByNameAndCity(
            @Param("city") String city,
            @Param("name") String name,
            @Param("openOnly") boolean openOnly,
            Pageable pageable
    );
}

