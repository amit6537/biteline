package com.fooddelivery.backend.controller;

import com.fooddelivery.backend.dto.request.ReviewRequest;
import com.fooddelivery.backend.dto.response.PartnerRatingResponse;
import com.fooddelivery.backend.dto.response.RatingSummaryResponse;
import com.fooddelivery.backend.dto.response.ReviewResponse;
import com.fooddelivery.backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ReviewController — Phase 11.
 *
 *   POST   /api/orders/{orderId}/review           customer reviews a delivered order
 *   PUT    /api/reviews/{id}                      author edits (within 24h)
 *   DELETE /api/reviews/{id}                      ADMIN removes (moderation)
 *   GET    /api/restaurants/{id}/reviews          public, newest first
 *   GET    /api/restaurants/{id}/rating-summary   public, avg + star breakdown
 *   GET    /api/delivery/rating                   partner's own delivery rating
 *
 * The two GET /api/restaurants/** routes are public through SecurityConfig.
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/orders/{orderId}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewResponse> create(@PathVariable UUID orderId,
                                                 @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(orderId, request));
    }

    @PutMapping("/api/reviews/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(reviewService.updateReview(id, request));
    }

    @DeleteMapping("/api/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/restaurants/{id}/reviews")
    public ResponseEntity<Page<ReviewResponse>> list(@PathVariable UUID id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.getRestaurantReviews(id, page, size));
    }

    @GetMapping("/api/restaurants/{id}/rating-summary")
    public ResponseEntity<RatingSummaryResponse> summary(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.getRatingSummary(id));
    }

    @GetMapping("/api/delivery/rating")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public ResponseEntity<PartnerRatingResponse> partnerRating() {
        return ResponseEntity.ok(reviewService.getMyPartnerRating());
    }
}
