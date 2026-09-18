package com.saas.workspace_service.dto;

import com.saas.workspace_service.entity.InvitationStatus;
import com.saas.workspace_service.entity.WorkspaceRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InvitationDto {
    private UUID id;
    private UUID workspaceId;
    private UUID projectId;
    private String email;
    private WorkspaceRole role;
    private String token;
    private InvitationStatus status;
    private UUID invitedBy;
    private Instant expiresAt;
}
