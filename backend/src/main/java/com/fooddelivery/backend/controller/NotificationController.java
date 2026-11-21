package com.fooddelivery.backend.controller;

import com.fooddelivery.backend.dto.response.NotificationResponse;
import com.fooddelivery.backend.service.NotificationService;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * NotificationController — the bell icon 🔔. Any logged-in user (any role).
 *
 *   GET   /api/notifications?unreadOnly=false&page=0&size=20   my inbox
 *   GET   /api/notifications/unread-count                      badge number
 *   PATCH /api/notifications/{id}/read                         mark one read
 *   PATCH /api/notifications/read-all                          mark all read
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getMyNotifications(unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        // userId is passed IN so it can be the cache key of @Cacheable
        Long count = notificationService.getUnreadCount(securityUtils.getCurrentUserId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead()));
    }
}
