package com.saas.auth_service.controller;

import com.saas.auth_service.dto.CreateNotificationRequest;
import com.saas.auth_service.dto.NotificationDto;
import jakarta.validation.Valid;
import com.saas.auth_service.security.UserPrincipal;
import com.saas.auth_service.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService service;

    /** Shared secret antar service. Tanpa default supaya startup gagal kalau env belum di-set. */
    @Value("${app.internal-token}")
    private String internalToken;

    @PostMapping("/internal")
    public ResponseEntity<NotificationDto> create(
            @RequestHeader(value = "X-Internal-Token", required = false) String presentedToken,
            @Valid @RequestBody CreateNotificationRequest request) {
        requireInternalToken(presentedToken);
        NotificationDto notification = service.createNotification(request);
        return notification != null ? ResponseEntity.ok(notification) : ResponseEntity.noContent().build();
    }

    /**
     * Endpoint ini permitAll di SecurityConfig karena dipanggil service lain tanpa JWT user,
     * jadi shared secret adalah satu-satunya kontrol akses. Dibandingkan dengan
     * MessageDigest.isEqual supaya waktu bandingnya tidak bergantung isi token.
     */
    private void requireInternalToken(String presentedToken) {
        byte[] expected = internalToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }
    }

    @GetMapping
    public ResponseEntity<Page<NotificationDto>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(service.getNotifications(principal.getId(), page, size, status, type));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("count", service.getUnreadCount(principal.getId())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.markAsRead(principal.getId(), id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("updated", service.markAllAsRead(principal.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        service.archive(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/read")
    public ResponseEntity<Map<String, Integer>> archiveAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("archived", service.archiveAllRead(principal.getId())));
    }
}
