package com.saas.auth_service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateNotificationRequest {

    private String recipientEmail;

    private UUID recipientUserId;

    @Size(max = 50)
    private String type;

    @Size(max = 255)
    private String title;

    @Size(max = 5000)
    private String content;

    private Map<String, Object> metadata;

    @Size(max = 20)
    private String priority;

    @Size(max = 1000)
    private String actionUrl;

    private Instant expiresAt;
}
