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
import com.fooddelivery.backend.util.PageUtils;
import com.fooddelivery.backend.util.GeoUtils;
import com.fooddelivery.backend.dto.response.NearbyRestaurantResponse;
import com.fooddelivery.backend.exception.BadRequestException;
import java.util.Map;
import com.fooddelivery.backend.config.CacheConfig;
import com.fooddelivery.backend.dto.response.CachedPage;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    private final CacheManager cacheManager;

    private static final double MAX_NEARBY_RADIUS_KM = 25.0;
    private static final double NEARBY_SPEED_KMH = 20.0;
    private static final double NEARBY_ROAD_FACTOR = 1.3;
    private static final long NEARBY_PARTNER_SEARCH_MIN = 5;

    /** Pages beyond this are never cached (protects Redis memory). */
    private static final int MAX_CACHED_PAGES = 5;

    // ═══════════════════════════════════════════════════════════
    // RESTAURANT OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Create a new restaurant — RESTAURANT_OWNER only.
     */
    // New restaurant → city lists are now out of date
    @CacheEvict(cacheNames = CacheConfig.RESTAURANT_LIST, allEntries = true)
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
                .avgPrepMinutes(request.getAvgPrepMinutes() != null ? request.getAvgPrepMinutes() : 20)
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
     * PageUtils.of(page, size, Sort.by("avgRating").descending())
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

        String normalizedCity = city.trim();
        Pageable pageable = PageUtils.of(page, size, Sort.by("avgRating").descending());

        // ── Cache-aside, written out by hand (no annotation) ───────
        // Only the first few pages are cached: otherwise anyone could fill
        // Redis by requesting ?page=1, 2, 3 ... 1000000 (one key each).
        boolean cacheable = pageable.getPageNumber() < MAX_CACHED_PAGES;
        String key = normalizedCity.toLowerCase() + ":" + openOnly + ":"
                + pageable.getPageNumber() + ":" + pageable.getPageSize();
        Cache cache = cacheManager.getCache(CacheConfig.RESTAURANT_LIST);

        // 1. Look in Redis
        if (cacheable) {
            CachedPage<RestaurantResponse> hit = safeGet(cache, key);
            if (hit != null) {
                return hit.toPage(); // HIT 🎯 — Postgres not touched
            }
        }

        // 2. MISS → ask Postgres
        Page<Restaurant> restaurants = openOnly
                ? restaurantRepository.findByCityIgnoreCaseAndIsOpenTrue(normalizedCity, pageable)
                : restaurantRepository.findByCityIgnoreCase(normalizedCity, pageable);
        Page<RestaurantResponse> result = restaurants.map(this::toRestaurantResponse);

        // 3. Save a copy for next time (expires after the TTL on its own)
        if (cacheable) {
            safePut(cache, key, CachedPage.from(result));
        }
        return result;
    }

    /**
     * Manual cache calls don't go through @Cacheable's error handler, so we
     * catch Redis errors ourselves: Redis down = treat as a miss, never a 500.
     */
    @SuppressWarnings("unchecked")
    private CachedPage<RestaurantResponse> safeGet(Cache cache, String key) {
        try {
            return cache.get(key, CachedPage.class);
        } catch (RuntimeException e) {
            log.warn("Cache read failed for {} (treating as miss): {}", key, e.getMessage());
            return null;
        }
    }

    private void safePut(Cache cache, String key, Object value) {
        try {
            cache.put(key, value);
        } catch (RuntimeException e) {
            log.warn("Cache write failed for {}: {}", key, e.getMessage());
        }
    }

    /**
     * Search restaurants by name within a city.
     */
    @Transactional(readOnly = true)
    public Page<RestaurantResponse> searchRestaurants(
            String city, String name, boolean openOnly, int page, int size) {

        Pageable pageable = PageUtils.of(page, size, Sort.by("avgRating").descending());
        return restaurantRepository
                .searchByNameAndCity(city, name, openOnly, pageable)
                .map(this::toRestaurantResponse);
    }

    /**
     * Get one restaurant by ID — public.
     */
    // Cache-aside with an annotation: Spring does steps 1-3 for us.
    // sync = true → if 100 requests miss at the same moment, only ONE goes to
    // Postgres; the rest wait for its result ("cache stampede" protection).
    @Cacheable(cacheNames = CacheConfig.RESTAURANT, key = "#id", sync = true)
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
    // Changed restaurant → drop its cached copy AND all city lists
    // (name, isOpen, etc. appear in the lists too)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT_LIST, allEntries = true)
    })
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
        if (request.getAvgPrepMinutes() != null) restaurant.setAvgPrepMinutes(request.getAvgPrepMinutes());

        return toRestaurantResponse(restaurantRepository.save(restaurant));
    }

    /**
     * Delete a restaurant — owner only.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.MENU, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT_LIST, allEntries = true)
    })
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
    /**
     * Phase 12: "restaurants near me" — nearest first, only ones that deliver
     * to this point, each with distance and a rough ETA.
     *
     * Not cached: every user has different coordinates, so the hit rate would
     * be ~0 and Redis would fill with one-off keys.
     */
    @Transactional(readOnly = true)
    public List<NearbyRestaurantResponse> findNearby(double lat, double lng, double radiusKm,
                                                     boolean openOnly, int limit) {
        // Edge cases: invalid input → 400 instead of nonsense / 500
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("Invalid coordinates.");
        }
        if (radiusKm <= 0 || radiusKm > MAX_NEARBY_RADIUS_KM) {
            throw new BadRequestException("radiusKm must be between 0 and " + MAX_NEARBY_RADIUS_KM + ".");
        }
        int safeLimit = Math.min(Math.max(limit, 1), PageUtils.MAX_PAGE_SIZE);

        // Bounding box: 1° latitude ≈ 111 km everywhere;
        // 1° longitude ≈ 111 km × cos(latitude) — shrinks towards the poles.
        double dLat = radiusKm / 111.0;
        double cosLat = Math.cos(Math.toRadians(lat));
        double dLng = cosLat < 0.01 ? 360 : radiusKm / (111.320 * cosLat);
        double minLng = lng - dLng, maxLng = lng + dLng;
        boolean useLngBox = minLng >= -180 && maxLng <= 180; // box would wrap around ±180°

        List<RestaurantRepository.NearbyRow> rows = restaurantRepository.findNearby(
                lat, lng, radiusKm, lat - dLat, lat + dLat, minLng, maxLng, useLngBox, openOnly, safeLimit);

        // Load the restaurants in ONE query, then put them back in distance order
        Map<UUID, Restaurant> byId = restaurantRepository
                .findAllById(rows.stream().map(RestaurantRepository.NearbyRow::getId).toList())
                .stream().collect(Collectors.toMap(Restaurant::getId, r -> r));

        return rows.stream()
                .filter(row -> byId.containsKey(row.getId()))
                .map(row -> {
                    Restaurant r = byId.get(row.getId());
                    double km = GeoUtils.round2(row.getDistanceKm());
                    long travel = GeoUtils.travelMinutes(km, NEARBY_SPEED_KMH, NEARBY_ROAD_FACTOR);
                    return NearbyRestaurantResponse.builder()
                            .restaurant(toRestaurantResponse(r))
                            .distanceKm(km)
                            .estimatedDeliveryMinutes(r.getAvgPrepMinutes() + NEARBY_PARTNER_SEARCH_MIN + travel)
                            .build();
                })
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.MENU, key = "#restaurantId", sync = true)
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
    @CacheEvict(cacheNames = CacheConfig.MENU, key = "#restaurantId") // menu changed → drop cached menu
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
    @CacheEvict(cacheNames = CacheConfig.MENU, key = "#restaurantId") // menu changed → drop cached menu
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
    @CacheEvict(cacheNames = CacheConfig.MENU, key = "#restaurantId") // menu changed → drop cached menu
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
                .avgPrepMinutes(r.getAvgPrepMinutes())
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

