package com.saas.project_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class TimeEntryResponse {
    private UUID id;
    private UUID projectId;
    private UUID taskId;
    private String taskTitle;
    private UUID userId;
    private String userName;
    private LocalDate entryDate;
    private BigDecimal hours;
    private String description;
    private String entryType;
    private String featureName;
    private Instant createdAt;
    private Instant updatedAt;
}