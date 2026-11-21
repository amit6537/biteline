package com.fooddelivery.backend.controller;

import com.fooddelivery.backend.dto.request.AdminAssignRequest;
import com.fooddelivery.backend.dto.response.DeliveryAssignmentResponse;
import com.fooddelivery.backend.dto.response.StuckOrderResponse;
import com.fooddelivery.backend.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AdminController — ops tools. ADMIN only.
 *
 *   GET  /api/admin/orders/unassigned        READY orders nobody is delivering
 *   POST /api/admin/orders/{id}/assign       give an order to a specific partner
 *
 * (Review moderation lives in ReviewController: DELETE /api/reviews/{id}.)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final DeliveryService deliveryService;

    @GetMapping("/orders/unassigned")
    public ResponseEntity<List<StuckOrderResponse>> unassignedOrders() {
        return ResponseEntity.ok(deliveryService.listUnassignedReadyOrders());
    }

    @PostMapping("/orders/{id}/assign")
    public ResponseEntity<DeliveryAssignmentResponse> assign(
            @PathVariable UUID id, @Valid @RequestBody AdminAssignRequest request) {
        return ResponseEntity.ok(deliveryService.adminAssign(id, request.getPartnerId()));
    }
}
