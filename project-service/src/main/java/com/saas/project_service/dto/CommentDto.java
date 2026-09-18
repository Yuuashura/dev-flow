package com.saas.project_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CommentDto {
    private UUID id;
    private UUID projectId;
    private String entityType;
    private UUID entityId;
    private UUID authorId;
    private String authorName;
    private String content;
    private boolean internal;
    private Instant createdAt;
}