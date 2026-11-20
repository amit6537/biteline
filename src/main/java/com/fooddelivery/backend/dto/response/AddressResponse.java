package com.fooddelivery.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AddressResponse {
    private UUID id;
    private String label;
    private String street;
    private String city;
    private String state;
    private String pincode;
    private Double latitude;
    private Double longitude;
    private boolean isDefault;
    private String formattedAddress;
    private LocalDateTime createdAt;
}

