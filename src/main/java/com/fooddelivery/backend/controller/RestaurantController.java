package com.fooddelivery.backend.controller;

import com.fooddelivery.backend.dto.request.CreateMenuItemRequest;
import com.fooddelivery.backend.dto.request.CreateRestaurantRequest;
import com.fooddelivery.backend.dto.request.UpdateMenuItemRequest;
import com.fooddelivery.backend.dto.request.UpdateRestaurantRequest;
import com.fooddelivery.backend.dto.response.MenuItemResponse;
import com.fooddelivery.backend.dto.response.RestaurantResponse;
import com.fooddelivery.backend.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RestaurantController — HTTP endpoints for restaurants and menus.
 *
 * CONCEPT: REST URL Design
 * ─────────────────────────
 * Good REST URLs follow a resource hierarchy:
 *   /api/restaurants              → collection of all restaurants
 *   /api/restaurants/{id}         → one restaurant
 *   /api/restaurants/{id}/menu    → menu belonging to a restaurant
 *   /api/restaurants/{id}/menu/{itemId} → one menu item
 *
 * HTTP verbs carry meaning:
 *   GET    → read (safe, idempotent)
 *   POST   → create a new resource
 *   PUT    → replace entire resource
 *   PATCH  → partial update
 *   DELETE → remove resource
 *
 * CONCEPT: @PreAuthorize
 * ───────────────────────
 * @PreAuthorize("hasRole('RESTAURANT_OWNER')") runs BEFORE the method.
 * If the user doesn't have that role → 403 Forbidden automatically.
 * This is enabled by @EnableMethodSecurity in SecurityConfig.
 *
 * Public endpoints (GET restaurants/menu) have NO @PreAuthorize —
 * they're open because SecurityConfig permits /api/restaurants/**.
 */
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    // ═══════════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS — no JWT required
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/restaurants?city=Mumbai&openOnly=true&page=0&size=20
     * Browse restaurants in a city with pagination.
     *
     * Query params:
     *   city     (required) — filter by city
     *   openOnly (default true) — only show open restaurants
     *   page     (default 0) — page number (0-based)
     *   size     (default 20) — items per page
     */
    @GetMapping
    public ResponseEntity<Page<RestaurantResponse>> getRestaurants(
            @RequestParam String city,
            @RequestParam(defaultValue = "true") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                restaurantService.getRestaurantsByCity(city, openOnly, page, size));
    }

    /**
     * GET /api/restaurants/search?city=Mumbai&name=pizza&openOnly=true
     * Search restaurants by name within a city.
     */
    @GetMapping("/search")
    public ResponseEntity<Page<RestaurantResponse>> searchRestaurants(
            @RequestParam String city,
            @RequestParam String name,
            @RequestParam(defaultValue = "true") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                restaurantService.searchRestaurants(city, name, openOnly, page, size));
    }

    /**
     * GET /api/restaurants/{id}
     * Get a specific restaurant's details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> getRestaurant(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getRestaurantById(id));
    }

    /**
     * GET /api/restaurants/{id}/menu
     * Get the full menu for a restaurant.
     */
    @GetMapping("/{id}/menu")
    public ResponseEntity<List<MenuItemResponse>> getMenu(@PathVariable UUID id) {
        return ResponseEntity.ok(restaurantService.getMenu(id));
    }

    // ═══════════════════════════════════════════════════════════
    // OWNER ENDPOINTS — RESTAURANT_OWNER role required
    // ═══════════════════════════════════════════════════════════

    /**
     * GET /api/restaurants/my
     * Get all restaurants owned by the authenticated user.
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<List<RestaurantResponse>> getMyRestaurants() {
        return ResponseEntity.ok(restaurantService.getMyRestaurants());
    }

    /**
     * POST /api/restaurants
     * Create a new restaurant.
     */
    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(restaurantService.createRestaurant(request));
    }

    /**
     * PATCH /api/restaurants/{id}
     * Partially update a restaurant (owner only — enforced in service).
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRestaurantRequest request) {

        return ResponseEntity.ok(restaurantService.updateRestaurant(id, request));
    }

    /**
     * DELETE /api/restaurants/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable UUID id) {
        restaurantService.deleteRestaurant(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // ═══════════════════════════════════════════════════════════
    // MENU ITEM ENDPOINTS
    // ═══════════════════════════════════════════════════════════

    /**
     * POST /api/restaurants/{id}/menu
     * Add a new menu item.
     */
    @PostMapping("/{id}/menu")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<MenuItemResponse> addMenuItem(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMenuItemRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(restaurantService.addMenuItem(id, request));
    }

    /**
     * PATCH /api/restaurants/{restaurantId}/menu/{itemId}
     * Update a menu item (toggle availability, change price, etc.)
     */
    @PatchMapping("/{restaurantId}/menu/{itemId}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateMenuItemRequest request) {

        return ResponseEntity.ok(
                restaurantService.updateMenuItem(restaurantId, itemId, request));
    }

    /**
     * DELETE /api/restaurants/{restaurantId}/menu/{itemId}
     */
    @DeleteMapping("/{restaurantId}/menu/{itemId}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<Void> deleteMenuItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId) {

        restaurantService.deleteMenuItem(restaurantId, itemId);
        return ResponseEntity.noContent().build();
    }
}

