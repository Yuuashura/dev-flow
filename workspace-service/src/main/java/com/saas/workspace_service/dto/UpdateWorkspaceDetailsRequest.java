package com.saas.workspace_service.dto;

import lombok.Data;

@Data
public class UpdateWorkspaceDetailsRequest {
    private String name;
    private String logoUrl;
    private String businessType;
    private String timezone;
}
