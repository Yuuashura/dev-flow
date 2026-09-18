package com.saas.integration_service.repository;

import com.saas.integration_service.entity.GithubInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, UUID> {
    List<GithubInstallation> findByWorkspaceId(UUID workspaceId);
    Optional<GithubInstallation> findByInstallationId(String installationId);
}
