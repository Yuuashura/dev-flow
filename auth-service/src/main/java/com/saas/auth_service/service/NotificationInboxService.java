package com.saas.auth_service.service;

import com.saas.auth_service.dto.CreateNotificationRequest;
import com.saas.auth_service.dto.NotificationDto;
import com.saas.auth_service.entity.UserNotification;
import com.saas.auth_service.repository.UserNotificationRepository;
import com.saas.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final UserNotificationRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public NotificationDto createNotification(CreateNotificationRequest request) {
        UUID recipientId = null;

        if (request.getRecipientUserId() != null) {
            recipientId = request.getRecipientUserId();
        } else if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
            recipientId = userRepository.findByEmail(request.getRecipientEmail().toLowerCase())
                    .map(user -> user.getId())
                    .orElse(null);
        }

        if (recipientId == null) return null;
        if (request.getType() == null || request.getType().isBlank()) return null;
        if (request.getTitle() == null || request.getTitle().isBlank()) return null;

        UserNotification notification = UserNotification.builder()
                .userId(recipientId)
                .type(request.getType().toUpperCase())
                .title(request.getTitle())
                .content(request.getContent())
                .metadata(request.getMetadata() != null ? request.getMetadata() : java.util.Map.of())
                .priority(request.getPriority() != null ? request.getPriority().toUpperCase() : "NORMAL")
                .actionUrl(request.getActionUrl())
                .expiresAt(request.getExpiresAt())
                .build();
        return toDto(repository.save(notification));
    }

    public Page<NotificationDto> getNotifications(UUID userId, int page, int size, String status, String type) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserNotification> notifications;
        if (type != null && !type.isBlank()) {
            notifications = repository.findByUserIdAndTypeAndStatusNot(userId, type.toUpperCase(), "ARCHIVED", pageable);
        } else if (status != null && !status.isBlank()) {
            notifications = repository.findByUserIdAndStatus(userId, status.toUpperCase(), pageable);
        } else {
            notifications = repository.findByUserIdAndStatusNot(userId, "ARCHIVED", pageable);
        }
        return notifications.map(this::toDto);
    }

    public long getUnreadCount(UUID userId) {
        return repository.countByUserIdAndStatus(userId, "UNREAD");
    }

    @Transactional
    public NotificationDto markAsRead(UUID userId, UUID notificationId) {
        UserNotification notification = findOwned(userId, notificationId);
        if ("UNREAD".equals(notification.getStatus())) {
            notification.setStatus("READ");
            notification.setReadAt(Instant.now());
        }
        return toDto(repository.save(notification));
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return repository.markAllAsRead(userId, Instant.now());
    }

    @Transactional
    public void archive(UUID userId, UUID notificationId) {
        UserNotification notification = findOwned(userId, notificationId);
        notification.setStatus("ARCHIVED");
        repository.save(notification);
    }

    @Transactional
    public int archiveAllRead(UUID userId) {
        return repository.archiveAllRead(userId);
    }

    private UserNotification findOwned(UUID userId, UUID notificationId) {
        return repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notifikasi tidak ditemukan"));
    }

    private NotificationDto toDto(UserNotification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .metadata(notification.getMetadata())
                .status(notification.getStatus())
                .priority(notification.getPriority())
                .actionUrl(notification.getActionUrl())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .expiresAt(notification.getExpiresAt())
                .build();
    }
}
