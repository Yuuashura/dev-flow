package com.saas.workspace_service.service;

import com.saas.workspace_service.dto.*;
import com.saas.workspace_service.entity.*;
import com.saas.workspace_service.event.EventPublisher;
import com.saas.workspace_service.event.InvitationCreatedEvent;
import com.saas.workspace_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final InvitationRepository invitationRepository;
    private final EventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public WorkspaceDto createWorkspace(UUID userId, CreateWorkspaceRequest request) {
        String name = validateWorkspaceName(request.getName());
        String slug = validateSlug(request.getSlug());
        String timezone = validateTimezone(request.getTimezone());
        String businessType = validateBusinessType(request.getBusinessType());
        String logoUrl = validateLogoUrl(request.getLogoUrl());

        if (workspaceRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug ini sudah dipakai. Pilih slug lain.");
        }

        int maxWorkspaces = planCaps(userId).maxWorkspaces();
        long owned = workspaceRepository.countByOwnerUserId(userId);
        if (owned >= maxWorkspaces) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "PLAN_LIMIT_REACHED: Anda telah mencapai batas " + maxWorkspaces + " workspace. Upgrade ke Premium untuk tanpa batas.");
        }

        Workspace workspace = Workspace.builder()
                .ownerUserId(userId)
                .name(name)
                .slug(slug)
                .logoUrl(logoUrl)
                .businessType(businessType)
                .timezone(timezone)
                .status(WorkspaceStatus.ACTIVE)
                .build();

        workspace = workspaceRepository.save(workspace);

        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(userId)
                .role(WorkspaceRole.OWNER)
                .status("ACTIVE")
                .build();

        memberRepository.save(ownerMember);

        return mapToDto(workspace, WorkspaceRole.OWNER);
    }

    /**
     * Workspace milik pengguna — termasuk yang ia masuki hanya lewat keanggotaan proyek.
     *
     * <p>Undangan proyek sengaja TIDAK menulis ke workspace_members: lihat
     * {@link #acceptInvitation}, yang menulis project_members lalu berhenti, supaya
     * penerimanya tidak ikut melihat proyek lain di workspace itu. Tapi daftar ini
     * dulu hanya membaca workspace_members, jadi hasilnya kosong untuk orang itu — dan
     * SelectWorkspacePage melempar daftar kosong ke /onboarding. Efeknya: diundang ke
     * sebuah proyek, menerima undangannya, lalu diminta membuat workspace sendiri,
     * tanpa satu pun jalan menuju proyek yang mengundangnya.
     *
     * <p>Sisa sistem sudah siap menerima anggota-proyek-saja —
     * ProjectService.getProjectsByWorkspace membaca project_members sebagai cadangan
     * dan hanya mengembalikan proyek yang bersangkutan. Hanya daftar ini yang tidak
     * pernah ikut pola itu, jadi di sinilah keanggotaan proyek berubah jadi pintu masuk.
     */
    public List<WorkspaceDto> getUserWorkspaces(UUID userId) {
        List<WorkspaceMember> memberships = memberRepository.findByUserId(userId);

        Map<UUID, WorkspaceRole> roleByWorkspace = new LinkedHashMap<>();
        for (WorkspaceMember m : memberships) {
            roleByWorkspace.put(m.getWorkspaceId(), m.getRole());
        }

        // Keanggotaan workspace menang: kalau sudah ada barisnya di atas, peran itu
        // yang dipakai dan peran proyek tidak boleh menimpanya.
        for (Map.Entry<UUID, WorkspaceRole> entry : projectOnlyWorkspaceRoles(userId).entrySet()) {
            roleByWorkspace.putIfAbsent(entry.getKey(), entry.getValue());
        }

        if (roleByWorkspace.isEmpty()) return List.of();

        // One query for all workspaces instead of one per membership.
        Map<UUID, Workspace> byId = workspaceRepository
                .findAllById(roleByWorkspace.keySet())
                .stream().collect(Collectors.toMap(Workspace::getId, w -> w));

        return roleByWorkspace.entrySet().stream()
                .map(e -> {
                    Workspace w = byId.get(e.getKey());
                    // A membership row pointing at a deleted workspace used to blow
                    // up the whole list with NoSuchElementException.
                    return w == null ? null : mapToDto(w, e.getValue());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Workspace yang hanya dijangkau lewat project_members, beserta peran efektifnya.
     *
     * <p>Satu orang bisa menjadi anggota beberapa proyek di workspace yang sama dengan
     * peran berbeda. Yang paling longgar yang dipakai, dan pemilihannya dibuat pasti:
     * CLIENT diurutkan paling akhir, lalu baris pertama per workspace yang diambil.
     * Tanpa urutan eksplisit, peran yang muncul bergantung urutan baris yang kebetulan
     * dikembalikan database.
     */
    private Map<UUID, WorkspaceRole> projectOnlyWorkspaceRoles(UUID userId) {
        List<Map.Entry<UUID, String>> rows = jdbcTemplate.query(
                // Tanpa DISTINCT: Postgres menolak SELECT DISTINCT yang ORDER BY-nya
                // memakai ekspresi di luar select list, dan duplikatnya toh sudah
                // dibuang putIfAbsent di bawah.
                "SELECT p.workspace_id AS workspace_id, pm.role AS role "
                        + "FROM project_members pm "
                        + "JOIN projects p ON p.id = pm.project_id "
                        + "WHERE pm.user_id = ? AND pm.status = 'ACTIVE' "
                        + "ORDER BY p.workspace_id, CASE WHEN pm.role = 'CLIENT' THEN 1 ELSE 0 END, pm.role",
                (rs, rowNum) -> Map.entry((UUID) rs.getObject("workspace_id"), rs.getString("role")),
                userId);

        Map<UUID, WorkspaceRole> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> row : rows) {
            try {
                result.putIfAbsent(row.getKey(), WorkspaceRole.valueOf(row.getValue()));
            } catch (IllegalArgumentException ignored) {
                // Peran yang tidak dikenal hanya melewatkan satu workspace, bukan
                // menggagalkan seluruh daftar milik pengguna.
            }
        }
        return result;
    }

    public WorkspaceDto getWorkspaceById(UUID workspaceId, UUID userId) {
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke workspace ini."));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        return mapToDto(workspace, member.getRole());
    }

    @Transactional
    public WorkspaceDto updateWorkspaceDetails(UUID workspaceId, UUID userId, UpdateWorkspaceDetailsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (!workspace.getOwnerUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only OWNER can update workspace details");
        }

        String name = validateWorkspaceName(request.getName());
        String timezone = validateTimezone(request.getTimezone());
        String businessType = validateBusinessType(request.getBusinessType());

        workspace.setName(name);
        workspace.setLogoUrl(validateLogoUrl(request.getLogoUrl()));
        workspace.setBusinessType(businessType);
        workspace.setTimezone(timezone);
        return mapToDto(workspaceRepository.save(workspace), WorkspaceRole.OWNER);
    }

    // ==================== VALIDASI ====================
    // Helper di bawah dipakai jalur create DAN update. Sebelumnya hanya
    // updateWorkspaceDetails yang mengecek timezone dan panjang nama, sementara
    // createWorkspace menyimpan mentah — jadi nilai yang ditolak saat diedit justru
    // bisa masuk saat dibuat.

    /** Kolom name varchar(255). */
    static final int MAX_WORKSPACE_NAME_LENGTH = 255;

    /** Kolom business_type VARCHAR(50) di V3__complete_saas_schema.sql. */
    static final int MAX_BUSINESS_TYPE_LENGTH = 50;

    /** Kolom timezone VARCHAR(50). */
    static final int MAX_TIMEZONE_LENGTH = 50;

    /** Kolom logo_url VARCHAR(500). */
    static final int MAX_LOGO_URL_LENGTH = 500;

    /** Slug dipakai verbatim sebagai segmen URL oleh frontend (/w/{slug}/...), jadi
     *  harus aman di path: huruf kecil, angka, dan tanda hubung di tengah. */
    static final java.util.regex.Pattern SLUG_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    /** Kolom invitations.email VARCHAR(255). @Email membatasi bentuk, bukan panjang,
     *  dan tidak ada jalur undangan yang memangkas atau membatasinya. */
    static String validateInviteEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email undangan wajib diisi.");
        }
        if (email.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email undangan maksimal 255 karakter.");
        }
        return email;
    }

    static String validateWorkspaceName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nama workspace wajib diisi.");
        }
        if (name.length() > MAX_WORKSPACE_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nama workspace maksimal " + MAX_WORKSPACE_NAME_LENGTH + " karakter.");
        }
        return name;
    }

    /** Mengembalikan slug yang sudah dinormalkan ke huruf kecil. Tanpa normalisasi,
     *  "Acme" dan "acme" jadi dua workspace berbeda yang menghasilkan URL yang sama. */
    static String validateSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (slug.length() < 2 || slug.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug harus 2-100 karakter.");
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slug hanya boleh huruf kecil, angka, dan tanda hubung di antaranya. Contoh: tim-produk.");
        }
        return slug;
    }

    static String validateTimezone(String raw) {
        String timezone = blankToNull(raw);
        if (timezone == null) {
            return null;
        }
        if (timezone.length() > MAX_TIMEZONE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Timezone maksimal " + MAX_TIMEZONE_LENGTH + " karakter.");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Timezone tidak dikenal. Contoh: Asia/Jakarta.", e);
        }
        return timezone;
    }

    static String validateBusinessType(String raw) {
        String businessType = blankToNull(raw);
        if (businessType != null && businessType.length() > MAX_BUSINESS_TYPE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Jenis usaha maksimal " + MAX_BUSINESS_TYPE_LENGTH + " karakter.");
        }
        return businessType;
    }

    /** Logo hanya boleh menunjuk media yang diunggah ke auth-service.
     *
     *  <p>Sebelumnya http(s) mana pun diterima. Skema sudah dicek, tapi itu tidak
     *  menjawab pertanyaan yang lebih penting: isi di ujung sana bisa berubah kapan
     *  saja setelah lolos, dan setiap anggota yang memuat halaman jadi menembak host
     *  milik orang lain. Sekarang byte-nya milik kita, sudah di-decode dan di-encode
     *  ulang saat unggah.
     */
    static final java.util.regex.Pattern MEDIA_REF = java.util.regex.Pattern.compile(
            "^/api/v1/media/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    static String validateLogoUrl(String raw) {
        String logoUrl = blankToNull(raw);
        if (logoUrl == null) {
            return null;
        }
        if (!MEDIA_REF.matcher(logoUrl).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Logo harus diunggah, bukan diisi tautan.");
        }
        return logoUrl;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean checkSlug(String slug) {
        return !workspaceRepository.existsBySlug(slug);
    }

    /** Dipakai untuk dropdown penerima tugas di halaman proyek, jadi setiap anggota
     *  (termasuk CLIENT) boleh membacanya — hanya nama dan peran, tanpa data sensitif. */
    public List<WorkspaceMemberDto> getWorkspaceMembers(UUID workspaceId, UUID userId) {
        memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke workspace ini."));
        return listWorkspaceMembers(workspaceId);
    }

    /**
     * Pengambilan datanya saja, tanpa cek izin.
     *
     * <p>Dipisahkan karena getProjectMembers sudah mengotorisasi lewat
     * effectiveProjectRole — yang sengaja meloloskan kolaborator khusus-proyek — lalu
     * memanggil getWorkspaceMembers, yang menegaskan ulang keanggotaan workspace dan
     * menolak orang yang sama dengan 403. Pemanggil yang sudah memeriksa izinnya
     * sendiri memakai method ini.
     */
    private List<WorkspaceMemberDto> listWorkspaceMembers(UUID workspaceId) {
        List<WorkspaceMember> members = memberRepository.findByWorkspaceId(workspaceId);
        if (members.isEmpty()) return List.of();

        // Satu query untuk semua nama, bukan satu per anggota.
        Map<UUID, String[]> profiles = new java.util.HashMap<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(members.size(), "?"));
        Object[] ids = members.stream().map(WorkspaceMember::getUserId).toArray();
        jdbcTemplate.query(
                "SELECT id, full_name, email FROM users WHERE id IN (" + placeholders + ")",
                rs -> {
                    profiles.put((UUID) rs.getObject("id"),
                            new String[] { rs.getString("full_name"), rs.getString("email") });
                },
                ids);

        return members.stream().map(member -> {
            String[] profile = profiles.get(member.getUserId());
            return WorkspaceMemberDto.builder()
                    .id(member.getId())
                    .workspaceId(member.getWorkspaceId())
                    .userId(member.getUserId())
                    .fullName(profile != null ? profile[0] : "Pengguna")
                    .email(profile != null ? profile[1] : null)
                    .role(member.getRole().name())
                    .status(member.getStatus())
                    .joinedAt(member.getJoinedAt())
                    .build();
        }).toList();
    }

    /** Caps that apply when the plan row cannot be read. Mirrors the FREE tier
     *  seeded by billing-service — never PRO, so a DB hiccup cannot hand out an
     *  unlimited plan the way the old Integer.MAX_VALUE fallback did. */
    private static final PlanCaps FREE_CAPS = new PlanCaps(3, 5);

    private record PlanCaps(int maxWorkspaces, int maxMembers) {}

    private PlanCaps planCaps(UUID userId) {
        String planCode;
        try {
            planCode = jdbcTemplate.queryForObject(
                    "SELECT plan_code FROM owner_plans WHERE user_id = ? AND active = true",
                    String.class, userId);
        } catch (EmptyResultDataAccessException e) {
            planCode = "FREE";
        }
        if (planCode == null || planCode.isBlank()) {
            planCode = "FREE";
        }
        try {
            PlanCaps caps = jdbcTemplate.queryForObject(
                    "SELECT max_workspaces, max_members_per_workspace FROM plans WHERE code = ?",
                    (rs, rowNum) -> new PlanCaps(rs.getInt(1), rs.getInt(2)), planCode);
            return caps != null ? caps : FREE_CAPS;
        } catch (EmptyResultDataAccessException e) {
            return FREE_CAPS;
        }
    }

    @Transactional
    public InvitationDto inviteMember(UUID workspaceId, UUID userId, InviteMemberRequest request) {
        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke workspace ini."));

        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya OWNER yang dapat mengundang anggota.");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace tidak ditemukan."));

        enforceSeatCap(workspaceId, workspace.getOwnerUserId());

        String token = UUID.randomUUID().toString();
        Invitation invitation = Invitation.builder()
                .workspaceId(workspaceId)
                .email(validateInviteEmail(request.getEmail()))
                .role(request.getRole())
                .token(token)
                .status(InvitationStatus.PENDING)
                .invitedBy(userId)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        invitation = invitationRepository.save(invitation);

        String acceptUrl = frontendUrl + "/accept-invitation?token=" + token;

        // Query database langsung untuk mendapatkan nama pengundang
        String inviterName = "Unknown";
        try {
            inviterName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?",
                    String.class,
                    userId);
        } catch (Exception e) {
            // Jika query gagal, gunakan fallback
            inviterName = "Unknown";
        }

        eventPublisher.publishInvitationCreated(
                InvitationCreatedEvent.builder()
                        .invitationId(invitation.getId())
                        .workspaceId(workspaceId)
                        .workspaceName(workspace.getName())
                        .email(invitation.getEmail())
                        .role(invitation.getRole())
                        .token(token)
                        .acceptUrl(acceptUrl)
                        .invitedBy(userId)
                        .inviterName(inviterName)
                        .expiresAt(invitation.getExpiresAt())
                        .build()
        );

        return mapToInvitationDto(invitation);
    }

    // ==================== UNDANGAN & ANGGOTA PER PROYEK ====================

    /** Peran pemanggil pada sebuah proyek: keanggotaan proyek diutamakan, lalu
     *  keanggotaan workspace sebagai cadangan. Sama seperti di project-service. */
    private String effectiveProjectRole(UUID projectId, UUID workspaceId, UUID userId) {
        List<String> projectRoles = jdbcTemplate.query(
                "SELECT role FROM project_members WHERE project_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("role"), projectId, userId);
        if (!projectRoles.isEmpty()) return projectRoles.get(0);

        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(m -> m.getRole().name())
                .orElse(null);
    }

    private UUID projectWorkspaceId(UUID projectId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT workspace_id FROM projects WHERE id = ?", UUID.class, projectId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan.");
        }
    }

    @Transactional
    /**
     * Batas kursi paket. Undangan PENDING ikut dihitung, kalau tidak cap-nya bisa
     * dilewati dengan mengirim semua undangan sekaligus.
     *
     * <p>Dipakai inviteMember DAN inviteProjectMember. Sebelumnya hanya jalur
     * workspace yang mengecek, sementara undangan proyek lolos tanpa hitungan sama
     * sekali — dan undangan proyek yang diterima tetap menambah baris di
     * project_members, jadi cap-nya bisa dilewati lewat pintu itu.
     */
    private void enforceSeatCap(UUID workspaceId, UUID ownerUserId) {
        int maxMembers = planCaps(ownerUserId).maxMembers();
        long used = memberRepository.countByWorkspaceId(workspaceId)
                + invitationRepository.findByWorkspaceIdAndStatus(workspaceId, InvitationStatus.PENDING).size();
        if (used >= maxMembers) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "PLAN_LIMIT_REACHED: Workspace ini sudah mencapai batas " + maxMembers
                            + " anggota. Upgrade ke Premium untuk tanpa batas.");
        }
    }

    public InvitationDto inviteProjectMember(UUID projectId, UUID userId, InviteMemberRequest request) {
        UUID workspaceId = projectWorkspaceId(projectId);
        String role = effectiveProjectRole(projectId, workspaceId, userId);

        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke proyek ini.");
        }
        if (!"OWNER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hanya OWNER proyek yang dapat mengundang anggota.");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace tidak ditemukan."));
        enforceSeatCap(workspaceId, workspace.getOwnerUserId());
        String inviteEmail = validateInviteEmail(request.getEmail());

        String token = UUID.randomUUID().toString();
        Invitation invitation = invitationRepository.save(Invitation.builder()
                .workspaceId(workspaceId)
                .projectId(projectId)
                .email(inviteEmail)
                .role(request.getRole())
                .token(token)
                .status(InvitationStatus.PENDING)
                .invitedBy(userId)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        String projectName = "Proyek";
        String inviterName = "Unknown";
        try {
            projectName = jdbcTemplate.queryForObject(
                    "SELECT name FROM projects WHERE id = ?", String.class, projectId);
            inviterName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?", String.class, userId);
        } catch (Exception ignored) {
            // Nama hanya untuk isi email; kegagalan di sini tidak boleh membatalkan undangan.
        }

        eventPublisher.publishInvitationCreated(
                InvitationCreatedEvent.builder()
                        .invitationId(invitation.getId())
                        .workspaceId(workspaceId)
                        .workspaceName(projectName)
                        .email(invitation.getEmail())
                        .role(invitation.getRole())
                        .token(token)
                        .acceptUrl(frontendUrl + "/accept-invitation?token=" + token)
                        .invitedBy(userId)
                        .inviterName(inviterName)
                        .expiresAt(invitation.getExpiresAt())
                        .build()
        );

        return mapToInvitationDto(invitation);
    }

    public List<InvitationDto> getProjectInvitations(UUID projectId, UUID userId) {
        UUID workspaceId = projectWorkspaceId(projectId);
        String role = effectiveProjectRole(projectId, workspaceId, userId);

        // DTO membawa token mentah, jadi hanya pengelola proyek yang boleh melihatnya.
        if (!"OWNER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hanya OWNER proyek yang dapat melihat undangan.");
        }

        return invitationRepository.findByProjectId(projectId).stream()
                .map(this::mapToInvitationDto)
                .toList();
    }

    /** Anggota yang punya akses ke proyek: kolaborator khusus-proyek digabung dengan
     *  anggota workspace, karena keduanya sama-sama bisa membuka proyek ini. */
    public List<WorkspaceMemberDto> getProjectMembers(UUID projectId, UUID userId) {
        UUID workspaceId = projectWorkspaceId(projectId);
        if (effectiveProjectRole(projectId, workspaceId, userId) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke proyek ini.");
        }

        List<WorkspaceMemberDto> result = new java.util.ArrayList<>(listWorkspaceMembers(workspaceId));
        java.util.Set<UUID> seen = result.stream()
                .map(WorkspaceMemberDto::getUserId)
                .collect(Collectors.toSet());

        jdbcTemplate.query(
                "SELECT pm.user_id, pm.role, pm.status, pm.joined_at, u.full_name, u.email "
                        + "FROM project_members pm JOIN users u ON u.id = pm.user_id "
                        + "WHERE pm.project_id = ? AND pm.status = 'ACTIVE'",
                rs -> {
                    UUID memberId = (UUID) rs.getObject("user_id");
                    if (seen.add(memberId)) {
                        result.add(WorkspaceMemberDto.builder()
                                .workspaceId(workspaceId)
                                .userId(memberId)
                                .fullName(rs.getString("full_name"))
                                .email(rs.getString("email"))
                                .role(rs.getString("role"))
                                .status(rs.getString("status"))
                                .joinedAt(rs.getTimestamp("joined_at").toInstant())
                                .build());
                    }
                },
                projectId);

        return result;
    }

    public List<InvitationDto> getWorkspaceInvitations(UUID workspaceId, UUID userId) {        WorkspaceMember member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke workspace ini."));

        // The DTO carries the raw invite token so the owner can copy the link.
        // Any member seeing that list could hand a pending OWNER invite to an
        // outsider, so this is OWNER-only — matching who can create them.
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya OWNER yang dapat melihat undangan.");
        }

        return invitationRepository.findByWorkspaceId(workspaceId)
                .stream()
                .map(this::mapToInvitationDto)
                .toList();
    }

    public InvitationDto getInvitationInfoByToken(String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token undangan tidak berlaku."));

        if (invitation.getStatus() != InvitationStatus.PENDING || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Undangan sudah kedaluwarsa atau tidak berlaku.");
        }

        return mapToInvitationDto(invitation);
    }

    @Transactional
    public void acceptInvitation(String token, UUID userId, String callerEmail) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token undangan tidak berlaku."));

        if (invitation.getStatus() != InvitationStatus.PENDING || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Undangan sudah kedaluwarsa atau tidak berlaku.");
        }

        // Without this the token is a bearer credential for the workspace: anyone
        // who gets the link (forwarded mail, a member reading the invitations
        // list) could join as the invited role, including OWNER.
        if (callerEmail == null || !callerEmail.equalsIgnoreCase(invitation.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Undangan ini ditujukan untuk " + invitation.getEmail()
                    + ". Masuk dengan akun email tersebut untuk menerimanya.");
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        // Undangan proyek hanya menulis ke project_members — penerimanya tidak
        // menjadi anggota workspace, jadi ia tidak melihat proyek lain di dalamnya.
        if (invitation.getProjectId() != null) {
            jdbcTemplate.update(
                    "INSERT INTO project_members (project_id, user_id, role, status, invited_by) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?) ON CONFLICT (project_id, user_id) DO NOTHING",
                    invitation.getProjectId(), userId, invitation.getRole().name(), invitation.getInvitedBy());
            return;
        }

        boolean alreadyMember = memberRepository.findByWorkspaceIdAndUserId(invitation.getWorkspaceId(), userId).isPresent();
        if (!alreadyMember) {
            WorkspaceMember member = WorkspaceMember.builder()
                    .workspaceId(invitation.getWorkspaceId())
                    .userId(userId)
                    .role(invitation.getRole())
                    .status("ACTIVE")
                    .build();

            memberRepository.save(member);
        }
    }

    private InvitationDto mapToInvitationDto(Invitation invitation) {
        return InvitationDto.builder()
                .id(invitation.getId())
                .workspaceId(invitation.getWorkspaceId())
                .projectId(invitation.getProjectId())
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .token(invitation.getToken())
                .status(invitation.getStatus())
                .invitedBy(invitation.getInvitedBy())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }

    private WorkspaceDto mapToDto(Workspace w, WorkspaceRole role) {
        return WorkspaceDto.builder()
                .id(w.getId())
                .ownerUserId(w.getOwnerUserId())
                .name(w.getName())
                .slug(w.getSlug())
                .logoUrl(w.getLogoUrl())
                .businessType(w.getBusinessType())
                .timezone(w.getTimezone())
                .status(w.getStatus())
                .userRole(role)
                .createdAt(w.getCreatedAt())
                .build();
    }
}
