package com.saas.integration_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class GithubCommitItem {
    private String sha;
    private String message;
    private String author;
    private String authorAvatar;
    private Instant committedAt;
    private String htmlUrl;
    private int additions;
    private int deletions;
}