package com.saas.workspace_service.repository;

import com.saas.workspace_service.entity.Invitation;
import com.saas.workspace_service.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByToken(String token);
    List<Invitation> findByProjectId(UUID projectId);
    List<Invitation> findByWorkspaceId(UUID workspaceId);
    List<Invitation> findByWorkspaceIdAndStatus(UUID workspaceId, InvitationStatus status);
}
