package com.fooddelivery.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
    @Getter(onMethod_ = @JsonProperty("isDefault")) // JSON name keeps the "is" prefix (Lombok getter would drop it)
    private boolean isDefault;
    private String formattedAddress;
    private LocalDateTime createdAt;
}

