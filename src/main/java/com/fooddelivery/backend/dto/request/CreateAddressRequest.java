package com.fooddelivery.backend.dto.request;

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

    private Double latitude;
    private Double longitude;

    private boolean isDefault = false;
}

