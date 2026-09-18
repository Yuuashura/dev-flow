package com.saas.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationRequestedEvent {

    private UUID userId;
    private String email;
    private String fullName;
    private String verificationToken;
    private String verificationUrl;
    private Instant expiresAt;
}
