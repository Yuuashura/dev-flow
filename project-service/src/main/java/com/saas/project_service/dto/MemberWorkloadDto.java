package com.saas.project_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class MemberWorkloadDto {
    private UUID userId;
    private String fullName;
    private String email;
    private String role;
    private String avatarUrl;
    private BigDecimal hoursThisWeek;
    private BigDecimal totalHours;
    private long tasksAssigned;
    private long tasksDone;
}