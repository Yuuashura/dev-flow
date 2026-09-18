package com.saas.workspace_service.event;

import com.saas.workspace_service.entity.WorkspaceRole;
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
public class InvitationCreatedEvent {
    private UUID invitationId;
    private UUID workspaceId;
    private String workspaceName;
    private String email;
    private WorkspaceRole role;
    private String token;
    private String acceptUrl;
    private UUID invitedBy;
    private String inviterName;
    private Instant expiresAt;
}
