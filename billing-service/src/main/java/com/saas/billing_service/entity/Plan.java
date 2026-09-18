package com.saas.billing_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "IDR";

    @Column(name = "billing_interval", nullable = false, length = 30)
    @Builder.Default
    private String billingInterval = "MONTHLY";

    @Column(name = "trial_days", nullable = false)
    @Builder.Default
    private int trialDays = 0;

    @Column(name = "max_workspaces")
    private Integer maxWorkspaces;

    @Column(name = "max_projects_per_workspace")
    private Integer maxProjectsPerWorkspace;

    @Column(name = "max_members_per_workspace")
    private Integer maxMembersPerWorkspace;

    @Column(name = "max_storage_gb")
    private Integer maxStorageGb;

    @Column(name = "github_repos")
    private Integer githubRepos;

    @Column(name = "milestones_per_project")
    private Integer milestonesPerProject;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
