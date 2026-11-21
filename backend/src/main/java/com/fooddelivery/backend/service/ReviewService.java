package com.fooddelivery.backend.service;

import com.fooddelivery.backend.config.CacheConfig;
import com.fooddelivery.backend.dto.request.ReviewRequest;
import com.fooddelivery.backend.dto.response.PartnerRatingResponse;
import com.fooddelivery.backend.dto.response.RatingSummaryResponse;
import com.fooddelivery.backend.dto.response.ReviewResponse;
import com.fooddelivery.backend.entity.*;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.exception.ConflictException;
import com.fooddelivery.backend.exception.ForbiddenException;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.kafka.OrderEventProducer;
import com.fooddelivery.backend.kafka.OrderEvents;
import com.fooddelivery.backend.repository.DeliveryAssignmentRepository;
import com.fooddelivery.backend.repository.OrderRepository;
import com.fooddelivery.backend.repository.RestaurantRepository;
import com.fooddelivery.backend.repository.ReviewRepository;
import com.fooddelivery.backend.util.PageUtils;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ReviewService — Phase 11: Reviews & Ratings.
 *
 * Rules (edge cases):
 *   - Only the customer who placed the order can review it        → 403
 *   - Only after it was DELIVERED (not cancelled / in progress)    → 400
 *   - Within N days of delivery (old orders can't be reviewed)     → 400
 *   - Exactly one review per order (double-click safe)             → 409
 *   - Delivery rating only if a partner actually delivered it      → 400
 *   - Edit only your own review, only within N hours               → 403 / 400
 *   - Admin can delete abusive reviews (moderation)
 *   - Blank comment → stored as null; ratings validated 1-5
 *
 * Every write updates the restaurant's running totals with ONE atomic SQL
 * statement and evicts the cached restaurant + city lists (avg rating is
 * shown and sorted on). Cache eviction runs AFTER commit (see CacheConfig).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final SecurityUtils securityUtils;
    private final OrderEventProducer eventProducer;

    @Value("${app.reviews.window-days:7}")
    private long reviewWindowDays;

    @Value("${app.reviews.edit-window-hours:24}")
    private long editWindowHours;

    // ── Create ───────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT, key = "#result.restaurantId"),
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT_LIST, allEntries = true)
    })
    @Transactional
    public ReviewResponse createReview(UUID orderId, ReviewRequest request) {
        User customer = securityUtils.getCurrentUser();

        // Lock the order: a double-clicked "Submit review" runs one at a time,
        // and the second one then sees the first review → 409.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ForbiddenException("You can only review your own orders.");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("You can review an order only after it is delivered.");
        }
        if (order.getDeliveredAt() != null
                && order.getDeliveredAt().isBefore(LocalDateTime.now().minusDays(reviewWindowDays))) {
            throw new BadRequestException("Reviews are accepted only within " + reviewWindowDays + " days of delivery.");
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new ConflictException("You have already reviewed this order. Edit it instead.");
        }

        // Who delivered it? (null if an admin marked it delivered without a partner)
        UUID partnerId = assignmentRepository
                .findFirstByOrderIdAndStatusIn(orderId, EnumSet.of(DeliveryAssignmentStatus.DELIVERED))
                .map(a -> a.getPartner().getId())
                .orElse(null);
        if (request.getDeliveryRating() != null && partnerId == null) {
            throw new BadRequestException("This order had no delivery partner to rate.");
        }

        LocalDateTime now = LocalDateTime.now();
        Review review = reviewRepository.saveAndFlush(Review.builder()
                .orderId(orderId)
                .customer(customer)
                .restaurantId(order.getRestaurant().getId())
                .partnerId(partnerId)
                .foodRating(request.getFoodRating())
                .deliveryRating(request.getDeliveryRating())
                .comment(cleanComment(request.getComment()))
                .createdAt(now)
                .updatedAt(now)
                .build());

        restaurantRepository.applyRatingChange(review.getRestaurantId(), review.getFoodRating(), +1);
        log.info("Review {} ({}★) for restaurant {}", review.getId(), review.getFoodRating(), review.getRestaurantId());

        eventProducer.publishReviewCreated(new OrderEvents.ReviewCreatedEvent(
                review.getId(), orderId, review.getRestaurantId(), partnerId,
                review.getFoodRating(), review.getDeliveryRating(), now));
        return toResponse(review);
    }

    // ── Edit ─────────────────────────────────────────────────

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT, key = "#result.restaurantId"),
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT_LIST, allEntries = true)
    })
    @Transactional
    public ReviewResponse updateReview(UUID reviewId, ReviewRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));

        if (!review.getCustomer().getId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own review.");
        }
        if (review.getCreatedAt().isBefore(LocalDateTime.now().minusHours(editWindowHours))) {
            throw new BadRequestException("Reviews can be edited only within " + editWindowHours + " hours.");
        }
        if (request.getDeliveryRating() != null && review.getPartnerId() == null) {
            throw new BadRequestException("This order had no delivery partner to rate.");
        }

        // Only the DIFFERENCE goes into the running total (4★ → 2★ = -2)
        int delta = request.getFoodRating() - review.getFoodRating();
        review.setFoodRating(request.getFoodRating());
        review.setDeliveryRating(request.getDeliveryRating());
        review.setComment(cleanComment(request.getComment()));
        review.setUpdatedAt(LocalDateTime.now());

        if (delta != 0) {
            restaurantRepository.applyRatingChange(review.getRestaurantId(), delta, 0);
        }
        return toResponse(review);
    }

    // ── Delete (admin moderation) ────────────────────────────

    /** @return the restaurant id, so the cache annotations know what to evict */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT, key = "#result"),
            @CacheEvict(cacheNames = CacheConfig.RESTAURANT_LIST, allEntries = true)
    })
    @Transactional
    public UUID deleteReview(UUID reviewId) {
        Review review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        reviewRepository.delete(review);
        restaurantRepository.applyRatingChange(review.getRestaurantId(), -review.getFoodRating(), -1);
        log.info("Admin deleted review {} of restaurant {}", reviewId, review.getRestaurantId());
        return review.getRestaurantId();
    }

    // ── Reads (public) ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getRestaurantReviews(UUID restaurantId, int page, int size) {
        ensureRestaurantExists(restaurantId);
        return reviewRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, PageUtils.of(page, size))
                .map(this::toResponse);
    }

    /**
     * Average + count come straight from the restaurant row (pre-computed),
     * only the per-star breakdown needs a GROUP BY.
     */
    @Transactional(readOnly = true)
    public RatingSummaryResponse getRatingSummary(UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) distribution.put(stars, 0L); // all keys, even if 0
        reviewRepository.countByStars(restaurantId)
                .forEach(row -> distribution.put(row.getStars(), row.getCount()));

        return RatingSummaryResponse.builder()
                .restaurantId(restaurantId)
                .avgRating(restaurant.getAvgRating())
                .totalReviews(restaurant.getTotalReviews())
                .distribution(distribution)
                .build();
    }

    @Transactional(readOnly = true)
    public PartnerRatingResponse getMyPartnerRating() {
        UUID partnerId = securityUtils.getCurrentUserId();
        ReviewRepository.PartnerRating r = reviewRepository.partnerRating(partnerId);
        Double avg = r.getAverage() == null ? null : Math.round(r.getAverage() * 100.0) / 100.0;
        return PartnerRatingResponse.builder()
                .partnerId(partnerId)
                .avgDeliveryRating(avg)
                .totalRatings(r.getCount())
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────

    private void ensureRestaurantExists(UUID restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found: " + restaurantId);
        }
    }

    /** "   " → null, "  nice  " → "nice". */
    private static String cleanComment(String comment) {
        if (comment == null) return null;
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Privacy: "Amit Sharma" → "Amit S." — never the full name or email. */
    private static String displayName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Customer";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .restaurantId(r.getRestaurantId())
                .reviewerName(displayName(r.getCustomer().getName()))
                .foodRating(r.getFoodRating())
                .deliveryRating(r.getDeliveryRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .edited(r.getUpdatedAt() != null && r.getUpdatedAt().isAfter(r.getCreatedAt()))
                .build();
    }
}
