package com.saas.workspace_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WorkspaceMemberDto {
    private UUID id;
    private UUID workspaceId;
    private UUID userId;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private Instant joinedAt;
}
