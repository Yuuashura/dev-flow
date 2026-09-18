package com.saas.project_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCommentRequest {
    private UUID entityId;          // diabaikan jika entityType=PROJECT (pakai projectId dari path)
    @NotBlank
    private String entityType;      // 'PROJECT' | 'TASK'
    @NotBlank
    private String content;
    private boolean internal = false;
}