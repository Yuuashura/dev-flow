package com.saas.auth_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class NotificationDto {
    private UUID id;
    private String type;
    private String title;
    private String content;
    private Map<String, Object> metadata;
    private String status;
    private String priority;
    private String actionUrl;
    private Instant createdAt;
    private Instant readAt;
    private Instant expiresAt;
}
