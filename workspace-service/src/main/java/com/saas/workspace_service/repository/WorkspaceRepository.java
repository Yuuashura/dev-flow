package com.saas.workspace_service.repository;

import com.saas.workspace_service.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findBySlug(String slug);
    boolean existsBySlug(String slug);
    long countByOwnerUserId(UUID ownerUserId);
}
