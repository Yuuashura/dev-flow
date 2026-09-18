package com.saas.billing_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimits {
    private String planCode;
    private int maxWorkspaces;
    private int maxProjectsPerWorkspace;
    private int maxMembersPerWorkspace;
    private int maxStorageGb;
    private int githubRepos;
    private int milestonesPerProject;
}
