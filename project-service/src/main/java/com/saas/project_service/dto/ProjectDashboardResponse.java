package com.saas.project_service.dto;

import com.saas.project_service.entity.Project;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ProjectDashboardResponse {
    private Project project;
    private int progressPercent;
    private BigDecimal totalHours;
    private BigDecimal hoursThisWeek;
    private BigDecimal hoursToday;
    private long memberCount;
    private long taskCount;
    private long doneTaskCount;
    private long overdueTaskCount;
    private List<TimeEntryResponse> recentEntries;
    private List<MemberWorkloadDto> members;
}