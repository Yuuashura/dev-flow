package com.saas.project_service.controller;

import com.saas.project_service.dto.ProjectDashboardResponse;
import com.saas.project_service.dto.TimeEntryRequest;
import com.saas.project_service.dto.TimeEntryResponse;
import com.saas.project_service.service.TimeTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectDashboardController {

    private final TimeTrackingService timeTrackingService;

    @GetMapping("/{id}/dashboard")
    public ResponseEntity<ProjectDashboardResponse> getDashboard(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(timeTrackingService.getDashboard(id, userId));
    }

    @GetMapping("/{id}/time-entries")
    public ResponseEntity<Page<TimeEntryResponse>> getTimeEntries(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            Pageable pageable) {
        return ResponseEntity.ok(timeTrackingService.getTimeEntries(id, userId, pageable));
    }

    @PostMapping("/{id}/time-entries")
    public ResponseEntity<TimeEntryResponse> createTimeEntry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody TimeEntryRequest request) {
        return ResponseEntity.ok(timeTrackingService.createTimeEntry(id, userId, request));
    }

    @PatchMapping("/{id}/time-entries/{entryId}")
    public ResponseEntity<TimeEntryResponse> updateTimeEntry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            @Valid @RequestBody TimeEntryRequest request) {
        return ResponseEntity.ok(timeTrackingService.updateTimeEntry(id, userId, entryId, request));
    }

    @DeleteMapping("/{id}/time-entries/{entryId}")
    public ResponseEntity<Void> deleteTimeEntry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @PathVariable UUID entryId) {
        timeTrackingService.deleteTimeEntry(id, userId, entryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/time-entries/recent")
    public ResponseEntity<List<TimeEntryResponse>> getRecentEntries(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(timeTrackingService.getRecentEntries(id, userId, limit));
    }

    @GetMapping("/{id}/time-entries/user/{targetUserId}")
    public ResponseEntity<List<TimeEntryResponse>> getUserTimeEntries(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @PathVariable UUID targetUserId) {
        return ResponseEntity.ok(timeTrackingService.getTimeEntriesByUser(id, targetUserId, userId));
    }
}