package com.fooddelivery.backend.service;

import com.fooddelivery.backend.dto.request.CreateAddressRequest;
import com.fooddelivery.backend.dto.response.AddressResponse;
import com.fooddelivery.backend.entity.Address;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.repository.AddressRepository;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {

    private final AddressRepository addressRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public AddressResponse createAddress(CreateAddressRequest request) {
        User user = securityUtils.getCurrentUser();

        // If this address is set to default, unset any existing default address for this user
        if (request.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(user.getId())
                    .ifPresent(existingDefault -> {
                        existingDefault.setDefault(false);
                        addressRepository.save(existingDefault);
                    });
        }

        Address address = Address.builder()
                .user(user)
                .label(request.getLabel())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isDefault(request.isDefault())
                .build();

        Address saved = addressRepository.save(address);
        log.info("Address created: {} for user {}", saved.getId(), user.getEmail());
        return toAddressResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses() {
        UUID userId = securityUtils.getCurrentUserId();
        return addressRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toAddressResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Address getAddressEntityByIdAndUser(UUID addressId, UUID userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found or unauthorized: " + addressId));
    }

    @Transactional
    public void deleteAddress(UUID addressId) {
        UUID userId = securityUtils.getCurrentUserId();
        Address address = getAddressEntityByIdAndUser(addressId, userId);
        addressRepository.delete(address);
    }

    private AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .pincode(address.getPincode())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .isDefault(address.isDefault())
                .formattedAddress(address.toFormattedAddress())
                .createdAt(address.getCreatedAt())
                .build();
    }
}

