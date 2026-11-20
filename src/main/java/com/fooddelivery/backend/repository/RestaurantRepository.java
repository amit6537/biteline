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

