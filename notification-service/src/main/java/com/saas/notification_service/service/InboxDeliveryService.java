package com.saas.notification_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
public class InboxDeliveryService {

    /** Satu instance dipakai ulang: ada connection pool dan timeout, tidak dibuat per panggilan. */
    private final RestClient restClient;

    public InboxDeliveryService(
            @Value("${app.auth-service-url:http://localhost:8081}") String authServiceUrl,
            // Tanpa default: secret tidak boleh ada di source, dan startup harus gagal
            // kalau NOTIFICATION_INTERNAL_TOKEN belum di-set.
            @Value("${app.internal-token}") String internalToken) {

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        // RestClient.builder() statis, bukan bean RestClient.Builder yang di-inject:
        // service ini tidak punya spring-boot-starter-webclient, jadi bean itu tidak
        // pernah di-autoconfigure dan startup gagal. Sama seperti BillingService.
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    public void createInvitationNotification(
            String recipientEmail,
            String inviterName,
            String workspaceName,
            String role,
            String token,
            String actionUrl,
            Instant expiresAt) {

        // Map.of melempar NPE kalau ada nilai null, jadi keempatnya diberi fallback.
        String safeWorkspaceName = workspaceName != null ? workspaceName : "Workspace";
        String safeRole = role != null ? role : "MEMBER";
        String safeInviterName = inviterName != null ? inviterName : "Pengguna DevFlow";
        String safeToken = token != null ? token : "";

        Map<String, Object> metadata = Map.of(
                "workspaceName", safeWorkspaceName,
                "role", safeRole,
                "inviterName", safeInviterName,
                "invitationToken", safeToken
        );
        createNotification(
                recipientEmail,
                "INVITATION",
                "Undangan ke " + safeWorkspaceName,
                safeInviterName + " mengundang Anda sebagai " + safeRole + ".",
                metadata,
                "HIGH",
                actionUrl,
                expiresAt
        );
    }

    public void createNotification(
            String recipientEmail,
            String type,
            String title,
            String content,
            Map<String, Object> metadata,
            String priority,
            String actionUrl,
            Instant expiresAt) {
        try {
            Map<String, Object> request = new java.util.HashMap<>();
            request.put("recipientEmail", recipientEmail);
            request.put("type", type);
            request.put("title", title);
            request.put("content", content);
            request.put("metadata", metadata != null ? metadata : Map.of());
            request.put("priority", priority);
            if (actionUrl != null) request.put("actionUrl", actionUrl);
            if (expiresAt != null) request.put("expiresAt", expiresAt.toString());

            restClient.post()
                    .uri("/api/v1/notifications/internal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            // 4xx tidak akan sembuh sendiri. 401/403 di sini berarti
            // NOTIFICATION_INTERNAL_TOKEN tidak cocok dengan milik auth-service, dan
            // konsekuensinya SETIAP notifikasi inbox gagal diam-diam sampai diperbaiki.
            // Tetap ditelan — email undangan sudah terkirim, melempar hanya memicu retry
            // yang mengirim ulang email yang sama — tapi dicatat sebagai masalah
            // konfigurasi, bukan sebagai satu kegagalan lepas.
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                log.error("Notifikasi inbox ditolak auth-service ({}). "
                                + "Periksa NOTIFICATION_INTERNAL_TOKEN — selama tidak cocok, "
                                + "semua notifikasi inbox akan gagal. Penerima: {}",
                        ex.getStatusCode(), recipientEmail);
            } else {
                log.error("Notifikasi inbox ditolak auth-service ({}) untuk {}: {}",
                        ex.getStatusCode(), recipientEmail, ex.getResponseBodyAsString());
            }
        } catch (Exception ex) {
            // Kegagalan sementara (auth-service sedang restart, jaringan). Sengaja
            // ditelan dengan alasan yang sama.
            log.warn("Gagal membuat notifikasi inbox untuk {}: {}", recipientEmail, ex.getMessage());
        }
    }
}
