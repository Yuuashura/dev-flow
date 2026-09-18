package com.saas.project_service.dto;

import lombok.Data;

@Data
public class UpdateProjectMetaRequest {
    private Integer progressPercent;
    private String demoUrl;
}