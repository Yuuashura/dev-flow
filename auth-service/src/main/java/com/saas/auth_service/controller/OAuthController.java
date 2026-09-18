package com.saas.auth_service.controller;

import com.saas.auth_service.dto.AuthResponse;
import com.saas.auth_service.security.UserPrincipal;
import com.saas.auth_service.service.GithubConnectService;
import com.saas.auth_service.service.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;
    private final GithubConnectService githubConnectService;

    // ==================== GOOGLE OAUTH ====================

    @GetMapping("/google/authorize")
    public ResponseEntity<Map<String, String>> getGoogleAuthorizeUrl() {
        return ResponseEntity.ok(oAuthService.getGoogleAuthorizeUrl());
    }

    @PostMapping("/google/callback")
    public ResponseEntity<AuthResponse> googleCallback(
            @Valid @RequestBody CallbackRequest request,
            HttpServletRequest servletRequest) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ipAddress = servletRequest.getRemoteAddr();

        AuthResponse response = oAuthService.handleGoogleCallback(
                request.getCode(), request.getState(), userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }

    // ==================== GITHUB OAUTH ====================

    @GetMapping("/github/authorize")
    public ResponseEntity<Map<String, String>> getGitHubAuthorizeUrl() {
        return ResponseEntity.ok(oAuthService.getGitHubAuthorizeUrl());
    }

    @PostMapping("/github/callback")
    public ResponseEntity<AuthResponse> githubCallback(
            @Valid @RequestBody CallbackRequest request,
            HttpServletRequest servletRequest) {
        String userAgent = servletRequest.getHeader("User-Agent");
        String ipAddress = servletRequest.getRemoteAddr();

        AuthResponse response = oAuthService.handleGitHubCallback(
                request.getCode(), request.getState(), userAgent, ipAddress);
        return ResponseEntity.ok(response);
    }

    // ==================== GITHUB REPO CONNECT (Vercel-style) ====================

    @GetMapping("/github/connect/authorize")
    public ResponseEntity<Map<String, String>> getGitHubConnectAuthorizeUrl() {
        return ResponseEntity.ok(githubConnectService.getConnectAuthorizeUrl());
    }

    @PostMapping("/github/connect/callback")
    public ResponseEntity<Map<String, Object>> githubConnectCallback(
            @Valid @RequestBody CallbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(githubConnectService.handleConnectCallback(
                request.getCode(), request.getState(), principal.getId()));
    }

    @Data
    public static class CallbackRequest {
        /** Tanpa batas, `code` disambung ke body permintaan token ke provider dan
         *  kegagalannya keluar sebagai 500. Panjangnya dibatasi longgar: format code
         *  milik provider, bukan milik kita. */
        @NotBlank(message = "Kode otorisasi wajib ada")
        @Size(max = 512, message = "Kode otorisasi tidak valid")
        private String code;

        @Size(max = 512, message = "State tidak valid")
        private String state;
    }
}
