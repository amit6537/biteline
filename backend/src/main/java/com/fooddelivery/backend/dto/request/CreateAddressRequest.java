package com.fooddelivery.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAddressRequest {

    private String label = "Home";

    @NotBlank(message = "Street is required")
    private String street;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100)
    private String state;

    @NotBlank(message = "Pincode is required")
    @Size(max = 10)
    private String pincode;

    // Optional, but if given must be a real coordinate — and BOTH or NEITHER
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    /**
     * Edge case: latitude without longitude (or the reverse) is useless and
     * would break distance maths. @AssertTrue runs this check during @Valid.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Provide both latitude and longitude, or neither")
    public boolean isCoordinatesComplete() {
        return (latitude == null) == (longitude == null);
    }

    @JsonProperty("isDefault") // keep the "is" prefix in JSON (Lombok would drop it)
    private boolean isDefault = false;
}

