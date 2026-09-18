package com.saas.workspace_service.controller;

import com.saas.workspace_service.dto.*;
import com.saas.workspace_service.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping("/workspaces")
    public ResponseEntity<WorkspaceDto> createWorkspace(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        return ResponseEntity.ok(workspaceService.createWorkspace(userId, request));
    }

    @GetMapping("/workspaces")
    public ResponseEntity<List<WorkspaceDto>> getUserWorkspaces(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(workspaceService.getUserWorkspaces(userId));
    }

    @GetMapping("/workspaces/{id}")
    public ResponseEntity<WorkspaceDto> getWorkspaceById(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.getWorkspaceById(id, userId));
    }

    @PutMapping("/workspaces/{id}/details")
    public ResponseEntity<WorkspaceDto> updateWorkspaceDetails(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkspaceDetailsRequest request) {
        return ResponseEntity.ok(workspaceService.updateWorkspaceDetails(id, userId, request));
    }

    @GetMapping("/workspaces/check-slug")
    public ResponseEntity<Boolean> checkSlug(@RequestParam String slug) {
        return ResponseEntity.ok(workspaceService.checkSlug(slug));
    }

    @PostMapping("/workspaces/{id}/invitations")
    public ResponseEntity<InvitationDto> inviteMember(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody InviteMemberRequest request) {
        return ResponseEntity.ok(workspaceService.inviteMember(id, userId, request));
    }

    @GetMapping("/workspaces/{id}/members")
    public ResponseEntity<List<WorkspaceMemberDto>> getWorkspaceMembers(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.getWorkspaceMembers(id, userId));
    }

    @GetMapping("/workspaces/{id}/invitations")
    public ResponseEntity<List<InvitationDto>> getWorkspaceInvitations(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.getWorkspaceInvitations(id, userId));
    }

    // Path di bawah /workspaces/** karena gateway mengarahkan /api/v1/projects/**
    // ke project-service. Keanggotaan tetap milik workspace-service.

    @PostMapping("/workspaces/projects/{projectId}/invitations")
    public ResponseEntity<InvitationDto> inviteProjectMember(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @Valid @RequestBody InviteMemberRequest request) {
        return ResponseEntity.ok(workspaceService.inviteProjectMember(projectId, userId, request));
    }

    @GetMapping("/workspaces/projects/{projectId}/invitations")
    public ResponseEntity<List<InvitationDto>> getProjectInvitations(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(workspaceService.getProjectInvitations(projectId, userId));
    }

    @GetMapping("/workspaces/projects/{projectId}/members")
    public ResponseEntity<List<WorkspaceMemberDto>> getProjectMembers(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(workspaceService.getProjectMembers(projectId, userId));
    }

    @GetMapping("/invitations/{token}")
    public ResponseEntity<InvitationDto> getInvitationInfo(@PathVariable String token) {
        return ResponseEntity.ok(workspaceService.getInvitationInfoByToken(token));
    }

    @PostMapping("/invitations/{token}/accept")
    public ResponseEntity<Void> acceptInvitation(
            @AuthenticationPrincipal UUID userId,
            Authentication authentication,
            @PathVariable String token) {
        String callerEmail = authentication != null ? (String) authentication.getCredentials() : null;
        workspaceService.acceptInvitation(token, userId, callerEmail);
        return ResponseEntity.ok().build();
    }
}
