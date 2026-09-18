package com.saas.integration_service.service;

import com.saas.integration_service.dto.GithubCommitItem;
import com.saas.integration_service.dto.GithubRepoItem;
import com.saas.integration_service.entity.*;
import com.saas.integration_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationService {

    private final GithubInstallationRepository installationRepository;
    private final NormalizedGithubActivityRepository activityRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.auth-service-url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${github.webhook-secret:}")
    private String githubWebhookSecret;

    // ==================== TENANCY / ROLE ====================
    // Cermin dari ProjectService: satu-satunya sumber kebenaran keanggotaan ada di
    // tabel workspace_members, jadi dibaca langsung lewat JDBC.

    private UUID getProjectWorkspaceId(UUID projectId) {
        List<UUID> ids = jdbcTemplate.query(
                "SELECT workspace_id FROM projects WHERE id = ?",
                (rs, rowNum) -> (UUID) rs.getObject("workspace_id"), projectId);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan");
        }
        return ids.get(0);
    }

    private String getMemberRole(UUID workspaceId, UUID userId) {
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM workspace_members WHERE workspace_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("role"), workspaceId, userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    private String getProjectRole(UUID projectId, UUID userId) {
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM project_members WHERE project_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("role"), projectId, userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    /**
     * Pemanggil harus punya akses ke proyek. Mengembalikan peran efektifnya.
     *
     * <p>Keanggotaan proyek diutamakan, workspace jadi cadangan — sama seperti
     * ProjectService.effectiveRole, TimeTrackingService.getMemberRole, dan
     * WorkspaceService.effectiveProjectRole. Service ini dulu hanya membaca
     * workspace_members, jadi kolaborator yang diundang ke satu proyek saja (tidak
     * punya baris di workspace_members) ditolak di sini padahal diterima di mana-mana.
     */
    private String enforceProjectMember(UUID projectId, UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tidak terautentikasi");
        }
        String role = getProjectRole(projectId, userId);
        if (role == null) {
            role = getMemberRole(getProjectWorkspaceId(projectId), userId);
        }
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke proyek ini.");
        }
        return role;
    }

    /** Menghubungkan / memutus repo mengubah apa yang dilihat klien, jadi CLIENT ditolak. */
    private void enforceProjectWritable(UUID projectId, UUID userId, String action) {
        if ("CLIENT".equals(enforceProjectMember(projectId, userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "CLIENT tidak dapat " + action);
        }
    }

    private void enforceWorkspaceMember(UUID workspaceId, UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tidak terautentikasi");
        }
        if (getMemberRole(workspaceId, userId) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota workspace ini");
        }
    }

    public List<GithubInstallation> getWorkspaceInstallations(UUID workspaceId, UUID userId) {
        enforceWorkspaceMember(workspaceId, userId);
        return installationRepository.findByWorkspaceId(workspaceId);
    }

    /** Aktivitas GitHub menumpuk selamanya: satu baris per push, tanpa akhir. Repo
     *  yang aktif mengumpulkan ribuan dalam hitungan bulan, dan mengembalikan semuanya
     *  memetakan tiap barisnya ke JSON hanya untuk dibuang oleh UI yang menampilkan
     *  sepuluh teratas. */
    static final int MAX_ACTIVITIES = 100;

    public List<NormalizedGithubActivity> getProjectActivities(UUID projectId, UUID userId) {
        return getProjectActivities(projectId, userId, MAX_ACTIVITIES);
    }

    public List<NormalizedGithubActivity> getProjectActivities(UUID projectId, UUID userId, int limit) {
        enforceProjectMember(projectId, userId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_ACTIVITIES));
        return activityRepository.findByProjectIdOrderByCreatedAtDesc(
                projectId, PageRequest.of(0, safeLimit));
    }

    /**
     * Verifikasi HMAC dulu, baru log. Normalisasi payload ke normalized_github_activities
     * belum diimplementasi, tapi tanpa verifikasi endpoint ini menerima payload dari
     * siapa pun — signature tetap wajib sekarang supaya tidak lupa saat normalisasi ditambah.
     */
    public void processWebhook(String eventType, String signature, String payload) {
        verifyGithubSignature(signature, payload);
        log.info("GitHub webhook received (type={})", eventType);
    }

    private void verifyGithubSignature(String signature, String payload) {
        if (githubWebhookSecret == null || githubWebhookSecret.isBlank()) {
            log.error("GITHUB_WEBHOOK_SECRET belum di-set, webhook ditolak");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook belum dikonfigurasi");
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature tidak ada");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(githubWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder expected = new StringBuilder("sha256=");
            for (byte b : digest) expected.append(String.format("%02x", b));

            // MessageDigest.isEqual: waktu banding tidak bergantung isi, jadi tidak bisa
            // dipakai menebak signature byte per byte.
            if (!MessageDigest.isEqual(
                    expected.toString().getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature tidak valid");
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal memverifikasi signature", ex);
        }
    }

    // ==================== GITHUB CONNECTION (Vercel-style) ====================

    private String getGithubLoginForUser(UUID userId) {
        List<String> logins = jdbcTemplate.query(
                "SELECT github_login FROM github_connections WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("github_login"), userId);
        return logins.isEmpty() ? null : logins.get(0);
    }

    public Map<String, Object> getConnectedAccount(UUID userId) {
        String login = getGithubLoginForUser(userId);
        if (login == null) {
            return Map.of("connected", false);
        }
        return Map.of("connected", true, "githubLogin", login);
    }

    private String findTokenForUser(UUID userId) {
        if (userId == null) return null;
        List<String> tokens = jdbcTemplate.query(
                "SELECT access_token FROM github_connections WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("access_token"), userId);
        return tokens.isEmpty() ? null : tokens.get(0);
    }

    public List<GithubRepoItem> listUserRepos(UUID userId) {
        String token = findTokenForUser(userId);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "GitHub belum terhubung. Silakan hubungkan akun GitHub terlebih dahulu.");
        }

        List<Map<String, Object>> body;
        try {
            body = githubGetList(token, "https://api.github.com/user/repos?per_page=100&sort=updated");
        } catch (Exception e) {
            log.error("Failed to list GitHub repos: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gagal memuat daftar repository GitHub.");
        }

        List<GithubRepoItem> repos = new ArrayList<>();
        if (body != null) {
            for (Map<String, Object> repo : body) {
                Map<String, Object> owner = (Map<String, Object>) repo.get("owner");
                repos.add(GithubRepoItem.builder()
                        .owner(owner != null ? String.valueOf(owner.get("login")) : null)
                        .name(String.valueOf(repo.get("name")))
                        .fullName(String.valueOf(repo.get("full_name")))
                        .description(repo.get("description") != null ? repo.get("description").toString() : null)
                        .defaultBranch(repo.get("default_branch") != null ? repo.get("default_branch").toString() : "main")
                        .htmlUrl(String.valueOf(repo.get("html_url")))
                        .privateRepo(Boolean.TRUE.equals(repo.get("private")))
                        .updatedAt(repo.get("updated_at") != null ? repo.get("updated_at").toString() : null)
                        .build());
            }
        }
        return repos;
    }

    public Map<String, Object> linkRepo(UUID userId, UUID projectId, String owner, String repo, String branch) {
        enforceProjectWritable(projectId, userId, "menghubungkan repository");

        String token = findTokenForUser(userId);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub belum terhubung.");
        }

        // Verifikasi repo dapat diakses dengan token ini, sekaligus ambil default branch
        // supaya kolom branch tidak tersimpan null (dulu disimpan null lalu tidak pernah diisi).
        Map<?, ?> repoInfo;
        try {
            repoInfo = githubGet(token, "https://api.github.com/repos/" + owner + "/" + repo, Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Repository tidak ditemukan atau tidak dapat diakses.");
        }

        String resolvedBranch = branch != null && !branch.isBlank()
                ? branch
                : repoInfo != null && repoInfo.get("default_branch") != null
                    ? repoInfo.get("default_branch").toString()
                    : "main";

        // github_linked_by menyimpan siapa pemilik token untuk repo ini. Tanpa itu,
        // pembacaan commit memakai token penonton — yang untuk CLIENT tidak ada,
        // sehingga repo private selalu 404 dari GitHub.
        jdbcTemplate.update(
                "UPDATE projects SET github_repo_owner = ?, github_repo_name = ?, github_repo_branch = ?, github_linked_by = ? WHERE id = ?",
                owner, repo, resolvedBranch, userId, projectId);

        return Map.of("linked", true, "owner", owner, "repo", repo, "branch", resolvedBranch);
    }

    public void unlinkRepo(UUID projectId, UUID userId) {
        enforceProjectWritable(projectId, userId, "memutus repository");
        jdbcTemplate.update(
                "UPDATE projects SET github_repo_owner = NULL, github_repo_name = NULL, github_repo_branch = NULL, github_linked_by = NULL WHERE id = ?",
                projectId);
    }

    public Map<String, Object> getLinkedRepo(UUID projectId, UUID userId) {
        enforceProjectMember(projectId, userId);
        LinkedRepo linked = findLinkedRepo(projectId);
        if (linked == null) {
            return Map.of("linked", false);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("linked", true);
        data.put("owner", linked.owner());
        data.put("repo", linked.repo());
        data.put("branch", linked.branch());
        return data;
    }

    private record LinkedRepo(String owner, String repo, String branch, UUID linkedBy) {}

    private LinkedRepo findLinkedRepo(UUID projectId) {
        List<LinkedRepo> rows = jdbcTemplate.query(
                "SELECT github_repo_owner, github_repo_name, github_repo_branch, github_linked_by FROM projects WHERE id = ?",
                (rs, rowNum) -> new LinkedRepo(
                        rs.getString("github_repo_owner"),
                        rs.getString("github_repo_name"),
                        rs.getString("github_repo_branch"),
                        (UUID) rs.getObject("github_linked_by")),
                projectId);
        if (rows.isEmpty() || rows.get(0).owner() == null) {
            return null;
        }
        return rows.get(0);
    }

    public List<GithubCommitItem> listProjectCommits(UUID userId, UUID projectId) {
        enforceProjectMember(projectId, userId);

        LinkedRepo linked = findLinkedRepo(projectId);
        if (linked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Proyek belum terhubung ke repository GitHub.");
        }

        // Token milik yang MENGHUBUNGKAN repo, bukan milik penonton. Seorang CLIENT
        // tidak punya koneksi GitHub sendiri; memakai tokennya membuat GitHub membalas
        // 404 untuk repo private (GitHub menyamarkan 403 jadi 404) dan riwayat commit
        // tampak kosong bagi klien.
        UUID tokenOwner = linked.linkedBy() != null ? linked.linkedBy() : userId;
        String token = findTokenForUser(tokenOwner);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY,
                    "Koneksi GitHub untuk repository ini sudah tidak aktif. "
                            + "Minta pemilik proyek menghubungkan ulang akun GitHub-nya.");
        }

        String url = "https://api.github.com/repos/" + linked.owner() + "/" + linked.repo() + "/commits?per_page=50";
        if (linked.branch() != null && !linked.branch().isBlank()) {
            url += "&sha=" + linked.branch();
        }

        List<Map<String, Object>> body;
        try {
            body = githubGetList(token, url);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY,
                    "Repository tidak dapat diakses dengan koneksi GitHub yang tersimpan. "
                            + "Akses mungkin sudah dicabut — hubungkan ulang repository.");
        } catch (Exception e) {
            log.error("Failed to fetch commits for project {}: {}", projectId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gagal memuat commit dari GitHub.");
        }

        List<GithubCommitItem> commits = new ArrayList<>();
        if (body != null) {
            for (Map<String, Object> c : body) {
                Map<String, Object> commit = (Map<String, Object>) c.get("commit");
                Map<String, Object> author = commit != null ? (Map<String, Object>) commit.get("author") : null;
                Map<String, Object> committerUser = (Map<String, Object>) c.get("author");
                String message = commit != null && commit.get("message") != null
                        ? commit.get("message").toString().split("\n")[0] : "";
                Instant committedAt = null;
                if (author != null && author.get("date") != null) {
                    try {
                        committedAt = OffsetDateTime.parse(author.get("date").toString()).toInstant();
                    } catch (Exception ignored) {}
                }
                commits.add(GithubCommitItem.builder()
                        .sha(c.get("sha") != null ? c.get("sha").toString() : null)
                        .message(message)
                        .author(author != null && author.get("name") != null ? author.get("name").toString() : "unknown")
                        .authorAvatar(committerUser != null && committerUser.get("avatar_url") != null
                                ? committerUser.get("avatar_url").toString() : null)
                        .committedAt(committedAt)
                        .htmlUrl(c.get("html_url") != null ? c.get("html_url").toString() : null)
                        .build());
            }
        }
        return commits;
    }

    // ==================== GITHUB HTTP ====================

    private HttpEntity<Void> githubEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("User-Agent", "DevFlow-SaaS-App");
        headers.set("Accept", "application/vnd.github+json");
        return new HttpEntity<>(headers);
    }

    private <T> T githubGet(String token, String url, Class<T> type) {
        return new RestTemplate().exchange(url, HttpMethod.GET, githubEntity(token), type).getBody();
    }

    private List<Map<String, Object>> githubGetList(String token, String url) {
        return new RestTemplate().exchange(url, HttpMethod.GET, githubEntity(token),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
    }
}