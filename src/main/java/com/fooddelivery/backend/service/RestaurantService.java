package com.fooddelivery.backend.service;

import com.fooddelivery.backend.dto.request.CreateMenuItemRequest;
import com.fooddelivery.backend.dto.request.CreateRestaurantRequest;
import com.fooddelivery.backend.dto.request.UpdateMenuItemRequest;
import com.fooddelivery.backend.dto.request.UpdateRestaurantRequest;
import com.fooddelivery.backend.dto.response.MenuItemResponse;
import com.fooddelivery.backend.dto.response.RestaurantResponse;
import com.fooddelivery.backend.entity.MenuItem;
import com.fooddelivery.backend.entity.Restaurant;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.exception.ForbiddenException;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.repository.MenuItemRepository;
import com.fooddelivery.backend.repository.RestaurantRepository;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RestaurantService — business logic for restaurants and menu items.
 *
 * CONCEPT: Mapper Methods (toResponse)
 * ──────────────────────────────────────
 * Private toResponse() methods convert Entity → DTO.
 * We keep this conversion in the service (not controller) because:
 *   - Business logic sometimes transforms data during mapping
 *     (e.g. price formatting, computed fields)
 *   - The controller stays thin and focused on HTTP only
 *
 * For large apps, dedicated Mapper classes (MapStruct) are preferred.
 * For now, private methods keep things simple and readable.
 *
 * CONCEPT: Ownership Checks
 * ──────────────────────────
 * Before any WRITE operation (update, delete), we verify:
 *   "Does the authenticated user OWN this restaurant?"
 * This prevents one owner from modifying another's restaurant.
 * This is AUTHORIZATION (what can you do?) vs AUTHENTICATION (who are you?).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final SecurityUtils securityUtils;

    // ═══════════════════════════════════════════════════════════
    // RESTAURANT OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Create a new restaurant — RESTAURANT_OWNER only.
     */
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        User owner = securityUtils.getCurrentUser();

        Restaurant restaurant = Restaurant.builder()
                .owner(owner)
                .name(request.getName())
                .description(request.getDescription())
                .cuisineType(request.getCuisineType())
                .imageUrl(request.getImageUrl())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .city(request.getCity().trim())
                .deliveryRadiusKm(request.getDeliveryRadiusKm() != null
                        ? request.getDeliveryRadiusKm() : 5.0)
                .minOrderAmount(request.getMinOrderAmount() != null
                        ? request.getMinOrderAmount().doubleValue() : 0.0)
                .build();

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant created: {} by owner {}", saved.getName(), owner.getEmail());
        return toRestaurantResponse(saved);
    }

    /**
     * Browse restaurants in a city — public, paginated.
     *
     * CONCEPT: Pageable
     * ──────────────────
     * PageRequest.of(page, size, Sort.by("avgRating").descending())
     *   page = 0-based page number
     *   size = items per page
     *   Sort = order by avg rating descending (most popular first)
     *
     * The returned Page<T> contains:
     *   .getContent()      → List of restaurants for this page
     *   .getTotalElements()→ total count across all pages
     *   .getTotalPages()   → how many pages exist
     *   .hasNext()         → is there a next page?
     */
    @Transactional(readOnly = true)
    public Page<RestaurantResponse> getRestaurantsByCity(
            String city, boolean openOnly, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("avgRating").descending());

        Page<Restaurant> restaurants = openOnly
                ? restaurantRepository.findByCityIgnoreCaseAndIsOpenTrue(city, pageable)
                : restaurantRepository.findByCityIgnoreCase(city, pageable);

        return restaurants.map(this::toRestaurantResponse);
    }

    /**
     * Search restaurants by name within a city.
     */
    @Transactional(readOnly = true)
    public Page<RestaurantResponse> searchRestaurants(
            String city, String name, boolean openOnly, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("avgRating").descending());
        return restaurantRepository
                .searchByNameAndCity(city, name, openOnly, pageable)
                .map(this::toRestaurantResponse);
    }

    /**
     * Get one restaurant by ID — public.
     */
    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantById(UUID id) {
        Restaurant restaurant = findRestaurantOrThrow(id);
        return toRestaurantResponse(restaurant);
    }

    /**
     * Get all restaurants owned by the current user.
     */
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getMyRestaurants() {
        UUID ownerId = securityUtils.getCurrentUserId();
        return restaurantRepository.findByOwnerId(ownerId).stream()
                .map(this::toRestaurantResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update a restaurant — owner only.
     *
     * CONCEPT: Partial Update (PATCH semantics)
     * ──────────────────────────────────────────
     * We only update fields that are non-null in the request.
     * This allows the client to send only the fields they want to change.
     * PUT would replace the entire resource; PATCH updates partially.
     */
    @Transactional
    public RestaurantResponse updateRestaurant(UUID id, UpdateRestaurantRequest request) {
        Restaurant restaurant = findAndVerifyOwnership(id);

        if (request.getName() != null) restaurant.setName(request.getName());
        if (request.getDescription() != null) restaurant.setDescription(request.getDescription());
        if (request.getCuisineType() != null) restaurant.setCuisineType(request.getCuisineType());
        if (request.getImageUrl() != null) restaurant.setImageUrl(request.getImageUrl());
        if (request.getIsOpen() != null) restaurant.setOpen(request.getIsOpen());
        if (request.getDeliveryRadiusKm() != null) restaurant.setDeliveryRadiusKm(request.getDeliveryRadiusKm());
        if (request.getMinOrderAmount() != null) restaurant.setMinOrderAmount(request.getMinOrderAmount().doubleValue());
        if (request.getLatitude() != null) restaurant.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) restaurant.setLongitude(request.getLongitude());

        return toRestaurantResponse(restaurantRepository.save(restaurant));
    }

    /**
     * Delete a restaurant — owner only.
     */
    @Transactional
    public void deleteRestaurant(UUID id) {
        Restaurant restaurant = findAndVerifyOwnership(id);
        restaurantRepository.delete(restaurant);
        log.info("Restaurant deleted: {}", id);
    }

    // ═══════════════════════════════════════════════════════════
    // MENU ITEM OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get full menu for a restaurant — public.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenu(UUID restaurantId) {
        findRestaurantOrThrow(restaurantId); // verify restaurant exists
        return menuItemRepository.findByRestaurantIdOrderByCategory(restaurantId).stream()
                .map(this::toMenuItemResponse)
                .collect(Collectors.toList());
    }

    /**
     * Add a menu item — owner only.
     */
    @Transactional
    public MenuItemResponse addMenuItem(UUID restaurantId, CreateMenuItemRequest request) {
        Restaurant restaurant = findAndVerifyOwnership(restaurantId);

        MenuItem item = MenuItem.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .isVegetarian(request.isVegetarian())
                .build();

        return toMenuItemResponse(menuItemRepository.save(item));
    }

    /**
     * Update a menu item — owner only.
     */
    @Transactional
    public MenuItemResponse updateMenuItem(UUID restaurantId, UUID itemId, UpdateMenuItemRequest request) {
        findAndVerifyOwnership(restaurantId); // must own the restaurant
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item not found: " + itemId));

        if (request.getName() != null) item.setName(request.getName());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getPrice() != null) item.setPrice(request.getPrice());
        if (request.getCategory() != null) item.setCategory(request.getCategory());
        if (request.getImageUrl() != null) item.setImageUrl(request.getImageUrl());
        if (request.getIsAvailable() != null) item.setAvailable(request.getIsAvailable());
        if (request.getIsVegetarian() != null) item.setVegetarian(request.getIsVegetarian());

        return toMenuItemResponse(menuItemRepository.save(item));
    }

    /**
     * Delete a menu item — owner only.
     */
    @Transactional
    public void deleteMenuItem(UUID restaurantId, UUID itemId) {
        findAndVerifyOwnership(restaurantId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Menu item not found: " + itemId));
        menuItemRepository.delete(item);
    }

    // ═══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private Restaurant findRestaurantOrThrow(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found: " + id));
    }

    /**
     * Find a restaurant AND verify the current user is its owner.
     * Used before any write operation.
     */
    private Restaurant findAndVerifyOwnership(UUID restaurantId) {
        UUID currentUserId = securityUtils.getCurrentUserId();
        return restaurantRepository.findByIdAndOwnerId(restaurantId, currentUserId)
                .orElseThrow(() -> new ForbiddenException(
                        "You don't own this restaurant or it doesn't exist"));
    }

    // ── Entity → DTO mappers ──────────────────────────────────

    private RestaurantResponse toRestaurantResponse(Restaurant r) {
        return RestaurantResponse.builder()
                .id(r.getId())
                .ownerId(r.getOwner().getId())
                .name(r.getName())
                .description(r.getDescription())
                .cuisineType(r.getCuisineType())
                .imageUrl(r.getImageUrl())
                .avgRating(r.getAvgRating())
                .totalReviews(r.getTotalReviews())
                .isOpen(r.isOpen())
                .deliveryRadiusKm(r.getDeliveryRadiusKm())
                .minOrderAmount(r.getMinOrderAmount())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .city(r.getCity())
                .build();
    }

    private MenuItemResponse toMenuItemResponse(MenuItem m) {
        return MenuItemResponse.builder()
                .id(m.getId())
                .restaurantId(m.getRestaurant().getId())
                .name(m.getName())
                .description(m.getDescription())
                .price(m.getPrice())
                .category(m.getCategory())
                .imageUrl(m.getImageUrl())
                .isAvailable(m.isAvailable())
                .isVegetarian(m.isVegetarian())
                .build();
    }
}

