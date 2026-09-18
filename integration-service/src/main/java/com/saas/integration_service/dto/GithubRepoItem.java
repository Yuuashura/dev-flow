package com.saas.integration_service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GithubRepoItem {
    private String owner;
    private String name;
    private String fullName;
    private String description;
    private String defaultBranch;
    private String htmlUrl;
    private boolean privateRepo;
    private String updatedAt;
}