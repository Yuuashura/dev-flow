package com.saas.project_service.controller;

import com.saas.project_service.dto.*;
import com.saas.project_service.entity.*;
import com.saas.project_service.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/projects")
    public ResponseEntity<Project> createProject(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(projectService.createProject(userId, request));
    }

    @GetMapping("/projects")
    public ResponseEntity<List<Project>> getProjectsByWorkspace(
            @AuthenticationPrincipal UUID userId,
            @RequestParam UUID workspaceId) {
        return ResponseEntity.ok(projectService.getProjectsByWorkspace(workspaceId, userId));
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<Project> getProjectById(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectById(id, userId));
    }

    @PatchMapping("/projects/{id}/meta")
    public ResponseEntity<Project> updateProjectMeta(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectMetaRequest request) {
        return ResponseEntity.ok(projectService.updateProjectMeta(id, userId, request));
    }

    @PutMapping("/projects/{id}/details")
    public ResponseEntity<Project> updateProjectDetails(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody ProjectDetailsRequest request) {
        return ResponseEntity.ok(projectService.updateProjectDetails(id, userId, request));
    }

    @PostMapping("/projects/{id}/tasks")
    public ResponseEntity<Task> createTask(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.ok(projectService.createTask(id, userId, request));
    }

    @GetMapping("/projects/{id}/tasks")
    public ResponseEntity<List<Task>> getProjectTasks(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectTasks(id, userId));
    }

    @PatchMapping("/tasks/{id}")
    public ResponseEntity<Task> updateTask(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(projectService.updateTask(id, userId, request));
    }

    /** Status dan alasannya satu body: keduanya tersimpan dalam satu transaksi.
     *  Dulu status lewat query param dan alasannya dikirim frontend lewat POST
     *  /comments yang terpisah — dua panggilan, dua kemungkinan gagal sendiri. */
    @PatchMapping("/tasks/{id}/status")
    public ResponseEntity<Task> updateTaskStatus(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        return ResponseEntity.ok(projectService.updateTaskStatus(
                id, userId, request.getStatus(), request.getReason()));
    }

    @GetMapping("/projects/{id}/milestones")
    public ResponseEntity<List<Milestone>> getProjectMilestones(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectMilestones(id, userId));
    }

    // ==================== COMMENTS ====================

    @PostMapping("/projects/{id}/comments")
    public ResponseEntity<CommentDto> addProjectComment(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(projectService.addComment(
                id, userId, request.getEntityType(), request.getEntityId(),
                request.getContent(), request.isInternal()));
    }

    @GetMapping("/projects/{id}/comments")
    public ResponseEntity<List<CommentDto>> getProjectComments(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "200") int limit) {
        // limit di-clamp server-side; nilai dari klien tidak menentukan batas atas.
        return ResponseEntity.ok(projectService.getComments(
                id, userId, entityType != null ? entityType : "PROJECT", entityId, limit));
    }

}
