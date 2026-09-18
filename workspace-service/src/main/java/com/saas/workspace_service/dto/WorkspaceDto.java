package com.saas.workspace_service.dto;

import com.saas.workspace_service.entity.WorkspaceRole;
import com.saas.workspace_service.entity.WorkspaceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WorkspaceDto {
    private UUID id;
    private UUID ownerUserId;
    private String name;
    private String slug;
    private String logoUrl;
    private String businessType;
    private String timezone;
    private WorkspaceStatus status;
    private WorkspaceRole userRole;
    private Instant createdAt;
}
