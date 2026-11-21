package com.fooddelivery.backend.controller;

import com.fooddelivery.backend.dto.request.UpdatePartnerStatusRequest;
import com.fooddelivery.backend.dto.response.DeliveryAssignmentResponse;
import com.fooddelivery.backend.dto.response.PartnerStatusResponse;
import com.fooddelivery.backend.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * DeliveryController — endpoints for the delivery partner's app.
 *
 * @PreAuthorize on the CLASS = every endpoint below needs DELIVERY_PARTNER.
 *
 *   PUT  /api/delivery/status                     go online/offline + location
 *   GET  /api/delivery/status                     my current status
 *   GET  /api/delivery/assignments/current        my current job (204 if none)
 *   GET  /api/delivery/assignments                my job history
 *   POST /api/delivery/assignments/{id}/reject    say no to a job
 *
 * Picking up / delivering stay on OrderController
 * (PATCH /api/orders/{id}/pickup and /deliver) because they change the ORDER.
 */
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PutMapping("/status")
    public ResponseEntity<PartnerStatusResponse> updateStatus(
            @Valid @RequestBody UpdatePartnerStatusRequest request) {
        return ResponseEntity.ok(deliveryService.updateMyStatus(request));
    }

    @GetMapping("/status")
    public ResponseEntity<PartnerStatusResponse> getStatus() {
        return ResponseEntity.ok(deliveryService.getMyStatus());
    }

    @GetMapping("/assignments/current")
    public ResponseEntity<DeliveryAssignmentResponse> getCurrentAssignment() {
        return deliveryService.getMyCurrentAssignment()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build()); // 204 = "no job right now"
    }

    @GetMapping("/assignments")
    public ResponseEntity<Page<DeliveryAssignmentResponse>> getAssignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(deliveryService.getMyAssignments(page, size));
    }

    /** Returns the NEW assignment (with the next partner), or 204 if nobody else is free yet. */
    @PostMapping("/assignments/{id}/reject")
    public ResponseEntity<DeliveryAssignmentResponse> rejectAssignment(@PathVariable UUID id) {
        return deliveryService.rejectAssignment(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
