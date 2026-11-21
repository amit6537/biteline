package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Used for both creating and editing a review (edit = full replacement). */
@Data
public class ReviewRequest {

    @NotNull(message = "Food rating is required")
    @Min(value = 1, message = "Rating must be 1-5")
    @Max(value = 5, message = "Rating must be 1-5")
    private Integer foodRating;

    // Optional: rate the delivery partner too
    @Min(value = 1, message = "Rating must be 1-5")
    @Max(value = 5, message = "Rating must be 1-5")
    private Integer deliveryRating;

    @Size(max = 1000, message = "Comment can be at most 1000 characters")
    private String comment;
}
