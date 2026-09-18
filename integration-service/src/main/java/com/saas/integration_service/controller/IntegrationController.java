package com.saas.integration_service.controller;

import com.saas.integration_service.dto.GithubCommitItem;
import com.saas.integration_service.dto.GithubRepoItem;
import com.saas.integration_service.dto.LinkRepoRequest;
import com.saas.integration_service.entity.*;
import com.saas.integration_service.service.IntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    /**
     * Endpoint publik (permitAll di SecurityConfig), jadi signature HMAC adalah satu-satunya
     * kontrol akses. Payload diterima sebagai String mentah supaya byte yang di-HMAC identik
     * dengan yang dikirim GitHub — deserialisasi ke objek akan mengubah byte dan merusak HMAC.
     */
    /** GitHub sendiri menolak payload di atas 25 MB. Endpoint ini permitAll, jadi tanpa
     *  batas siapa pun bisa memaksa service membaca dan menahan body sebesar apa pun di
     *  memori sebelum HMAC sempat dihitung. */
    private static final int MAX_WEBHOOK_PAYLOAD_BYTES = 25 * 1024 * 1024;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {
        if (payload != null && payload.length() > MAX_WEBHOOK_PAYLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Payload webhook terlalu besar.");
        }
        integrationService.processWebhook(eventType, signature, payload);
        return ResponseEntity.ok("Webhook received");
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<List<GithubInstallation>> getInstallations(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(integrationService.getWorkspaceInstallations(workspaceId, userId));
    }

    @GetMapping("/projects/{projectId}/activities")
    public ResponseEntity<List<NormalizedGithubActivity>> getActivities(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "100") int limit) {
        // limit di-clamp server-side; nilai klien tidak menentukan batas atas.
        return ResponseEntity.ok(integrationService.getProjectActivities(projectId, userId, limit));
    }

    // ==================== GITHUB CONNECTION (Vercel-style) ====================

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getConnectedAccount(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(integrationService.getConnectedAccount(userId));
    }

    @GetMapping("/repos")
    public ResponseEntity<List<GithubRepoItem>> listRepos(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(integrationService.listUserRepos(userId));
    }

    @GetMapping("/projects/{projectId}/repo")
    public ResponseEntity<Map<String, Object>> getLinkedRepo(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(integrationService.getLinkedRepo(projectId, userId));
    }

    @PostMapping("/projects/{projectId}/repo")
    public ResponseEntity<Map<String, Object>> linkRepo(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId,
            @Valid @RequestBody LinkRepoRequest request) {
        return ResponseEntity.ok(integrationService.linkRepo(userId, projectId,
                request.getOwner(), request.getRepo(), request.getBranch()));
    }

    @DeleteMapping("/projects/{projectId}/repo")
    public ResponseEntity<Void> unlinkRepo(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        integrationService.unlinkRepo(projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/commits")
    public ResponseEntity<List<GithubCommitItem>> listCommits(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(integrationService.listProjectCommits(userId, projectId));
    }
}