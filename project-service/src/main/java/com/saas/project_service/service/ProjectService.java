package com.saas.project_service.service;

import com.saas.project_service.dto.*;
import com.saas.project_service.entity.*;
import com.saas.project_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final CommentRepository commentRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Kolom name di Postgres varchar(255); tanpa batas di sini request yang lebih panjang
     *  baru gagal di driver sebagai 500, bukan 400 yang bisa ditampilkan ke user. */
    static final int MAX_NAME_LENGTH = 255;

    /** 10.000 jam ≈ 5 tahun kerja penuh untuk satu Flow. Di atas itu hampir pasti salah
     *  ketik, dan angkanya merusak skala grafik beban kerja. */
    static final java.math.BigDecimal MAX_ESTIMATED_HOURS = new java.math.BigDecimal("10000");

    // ==================== ROLE / MEMBERSHIP HELPERS ====================

    private String getMemberRole(UUID workspaceId, UUID userId) {
        try {
            List<String> roles = jdbcTemplate.query(
                    "SELECT role FROM workspace_members WHERE workspace_id = ? AND user_id = ? AND status = 'ACTIVE'",
                    (rs, rowNum) -> rs.getString("role"),
                    workspaceId, userId);
            return roles.isEmpty() ? null : roles.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceMember(UUID workspaceId, UUID userId) {
        String role = getMemberRole(workspaceId, userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke workspace ini.");
        }
    }

    private boolean isClient(UUID workspaceId, UUID userId) {
        return "CLIENT".equals(getMemberRole(workspaceId, userId));
    }

    private void enforceWritable(UUID workspaceId, UUID userId, String action) {
        enforceMember(workspaceId, userId);
        if (isClient(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CLIENT tidak dapat melakukan aksi ini: " + action);
        }
    }

    // ---- Berlapis: keanggotaan proyek diutamakan, workspace jadi cadangan ----
    // Seseorang yang diundang ke satu proyek tidak punya baris di workspace_members,
    // jadi pengecekan level workspace saja akan menolaknya. Sebaliknya anggota
    // workspace lama tidak punya baris di project_members, jadi cadangan itu wajib
    // ada supaya akses mereka tidak hilang.

    private String getProjectRole(UUID projectId, UUID userId) {
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM project_members WHERE project_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("role"), projectId, userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    /** Peran efektif pengguna pada sebuah proyek, atau null bila tidak punya akses. */
    private String effectiveRole(Project project, UUID userId) {
        String projectRole = getProjectRole(project.getId(), userId);
        return projectRole != null ? projectRole : getMemberRole(project.getWorkspaceId(), userId);
    }

    private String enforceProjectAccess(Project project, UUID userId) {
        String role = effectiveRole(project, userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke proyek ini.");
        }
        // clientVisible dulu diterima dari request body, disimpan, lalu tidak pernah
        // dikonsultasi jalur baca mana pun — janji visibilitas yang tidak ditepati API.
        // Sekarang ditegakkan di titik yang sama dengan pengecekan akses lain.
        if (!project.isClientVisible() && "CLIENT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Proyek ini tidak dibagikan ke klien.");
        }
        return role;
    }

    private void enforceProjectWritable(Project project, UUID userId, String action) {
        if ("CLIENT".equals(enforceProjectAccess(project, userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CLIENT tidak dapat melakukan aksi ini: " + action);
        }
    }

    // ==================== PROJECTS ====================

    /** Cap applied when the plan row cannot be read. Mirrors the FREE tier seeded
     *  by billing-service. The old code fell back to Integer.MAX_VALUE, so any
     *  transient SQL error silently granted unlimited projects. */
    private static final int FREE_MAX_PROJECTS = 3;

    private void enforceProjectLimit(UUID workspaceId) {
        String planCode = "FREE";
        try {
            UUID ownerUserId = jdbcTemplate.queryForObject(
                    "SELECT owner_user_id FROM workspaces WHERE id = ?", UUID.class, workspaceId);
            if (ownerUserId != null) {
                String code = jdbcTemplate.queryForObject(
                        "SELECT plan_code FROM owner_plans WHERE user_id = ? AND active = true",
                        String.class, ownerUserId);
                if (code != null && !code.isBlank()) planCode = code;
            }
        } catch (EmptyResultDataAccessException e) {
            planCode = "FREE";
        }

        int maxProjects;
        try {
            Integer m = jdbcTemplate.queryForObject(
                    "SELECT max_projects_per_workspace FROM plans WHERE code = ?", Integer.class, planCode);
            maxProjects = m != null ? m : FREE_MAX_PROJECTS;
        } catch (EmptyResultDataAccessException e) {
            maxProjects = FREE_MAX_PROJECTS;
        }

        if (maxProjects != Integer.MAX_VALUE) {
            long existing = projectRepository.countByWorkspaceId(workspaceId);
            if (existing >= maxProjects) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "PLAN_LIMIT_REACHED: Anda telah mencapai batas " + maxProjects + " proyek per workspace. Upgrade ke Premium untuk tanpa batas.");
            }
        }
    }

    @Transactional
    public Project createProject(UUID userId, CreateProjectRequest request) {
        enforceWritable(request.getWorkspaceId(), userId, "membuat proyek");
        enforceProjectLimit(request.getWorkspaceId());

        String name = validateProjectSchedule(request.getName(), request.getStartDate(), request.getTargetDate());

        Project project = Project.builder()
                .workspaceId(request.getWorkspaceId())
                .name(name)
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .targetDate(request.getTargetDate())
                .clientVisible(request.isClientVisible())
                .createdBy(userId)
                .status(ProjectStatus.PLANNING)
                .build();

        return projectRepository.save(project);
    }

    /** Anggota workspace melihat semua proyek; orang yang hanya diundang ke proyek
     *  tertentu melihat proyek itu saja. Tanpa cabang kedua ia mendapat "access
     *  denied" dan sidebar-nya kosong meski undangannya sudah diterima. */
    public List<Project> getProjectsByWorkspace(UUID workspaceId, UUID userId) {
        String workspaceRole = getMemberRole(workspaceId, userId);
        if (workspaceRole != null) {
            List<Project> all = projectRepository.findByWorkspaceId(workspaceId);
            if (!"CLIENT".equals(workspaceRole)) {
                return all;
            }
            // Sejalan dengan enforceProjectAccess: proyek yang tidak dibagikan ke klien
            // tidak boleh muncul di daftarnya.
            return all.stream().filter(Project::isClientVisible).toList();
        }

        List<UUID> projectIds = jdbcTemplate.query(
                "SELECT project_id FROM project_members WHERE user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> (UUID) rs.getObject("project_id"), userId);
        if (projectIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses ke workspace ini.");
        }

        return projectRepository.findAllById(projectIds).stream()
                .filter(p -> workspaceId.equals(p.getWorkspaceId()))
                .toList();
    }

    public Project getProjectById(UUID id, UUID userId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan."));
        enforceProjectAccess(project, userId);
        return project;
    }

    @Transactional
    public Project updateProjectDetails(UUID projectId, UUID userId, ProjectDetailsRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        String role = effectiveRole(project, userId);
        // WorkspaceRole hanya punya OWNER, DEVELOPER, CLIENT — tidak ada ADMIN.
        // Pemeriksaan ini dulu juga membandingkan dengan "ADMIN", perbandingan yang
        // tidak pernah bisa benar, dan pesannya menyebut peran yang tidak pernah ada.
        if (!"OWNER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Hanya OWNER yang dapat mengubah detail proyek.");
        }

        String name = validateProjectSchedule(request.getName(), request.getStartDate(), request.getTargetDate());
        enforceNoFlowOutsideWindow(projectId, request.getStartDate(), request.getTargetDate());

        project.setName(name);
        project.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim());
        project.setStartDate(request.getStartDate());
        project.setTargetDate(request.getTargetDate());
        return projectRepository.save(project);
    }

    // ==================== PROGRESS & DEMO ====================

    @Transactional
    public Project updateProjectMeta(UUID projectId, UUID userId, UpdateProjectMetaRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan."));
        enforceProjectWritable(project, userId, "memperbarui progres/demo proyek");

        // progressPercent sengaja TIDAK lagi diambil dari request. Angkanya sekarang
        // diturunkan dari Flow yang selesai (lihat recalculateProgress), jadi menerimanya
        // dari body hanya membuka jalan menimpa nilai yang benar — dan itu memang yang
        // terjadi: frontend memanggil endpoint ini untuk menyimpan URL demo sambil
        // mengirim progressPercent basi, sehingga setiap simpan demo mengembalikannya ke 0.
        if (request.getDemoUrl() != null) {
            project.setDemoUrl(validateDemoUrl(request.getDemoUrl()));
        }

        return projectRepository.save(project);
    }

    // ==================== PROGRES TURUNAN ====================

    /**
     * Progres proyek dihitung dari Flow yang selesai, bukan diketik manual.
     *
     * <p>Sebelumnya {@code progressPercent} hanya pernah ditulis oleh endpoint
     * updateProjectMeta, dan tidak ada satu pun kontrol di UI yang mengubahnya —
     * jadi angkanya tidak pernah bergerak dari 0 seberapa pun banyak Flow yang
     * diselesaikan. Sekarang dihitung ulang di setiap perubahan Flow.
     *
     * <p>Status proyek ikut mengikuti: ada Flow yang jalan berarti proyek tidak lagi
     * PLANNING, dan semua Flow selesai berarti COMPLETED. Status yang dipilih manusia
     * secara eksplisit — ON_HOLD dan CANCELLED — tidak diutak-atik.
     */
    private void recalculateProgress(Project project) {
        long total = taskRepository.countByProjectId(project.getId());
        if (total == 0) {
            project.setProgressPercent(0);
            if (project.getStatus() == ProjectStatus.COMPLETED) {
                project.setStatus(ProjectStatus.IN_PROGRESS);
            }
            projectRepository.save(project);
            return;
        }

        long done = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.DONE);
        // Pembulatan ke bawah, kecuali saat semuanya selesai: 7 dari 8 Flow tidak boleh
        // dibulatkan menjadi 100% dan membuat proyek tampak beres padahal belum.
        int percent = done == total ? 100 : (int) ((done * 100) / total);
        project.setProgressPercent(percent);

        if (percent >= 100) {
            project.setStatus(ProjectStatus.COMPLETED);
        } else if (project.getStatus() == ProjectStatus.PLANNING
                || project.getStatus() == ProjectStatus.COMPLETED) {
            // COMPLETED ikut dibalik: menambah Flow baru ke proyek yang sudah selesai
            // membuatnya berjalan lagi.
            project.setStatus(ProjectStatus.IN_PROGRESS);
        }
        projectRepository.save(project);
    }

    // ==================== TASKS ====================

    @Transactional
    public Task createTask(UUID projectId, UUID userId, CreateTaskRequest request) {
        Project project = getProjectById(projectId, userId);
        enforceProjectWritable(project, userId, "membuat task");

        if (request.getAssignedTo() != null) {
            enforceAssigneeIsMember(project, request.getAssignedTo());
        }
        String title = validateFlowTitle(request.getTitle());
        validateTaskSchedule(request.getStartDate(), request.getDueDate(), request.getEstimatedHours(),
                project.getStartDate(), project.getTargetDate());

        Task task = Task.builder()
                .projectId(project.getId())
                .title(title)
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
                .assignedTo(request.getAssignedTo())
                .startDate(request.getStartDate())
                .dueDate(request.getDueDate())
                .estimatedHours(request.getEstimatedHours())
                .createdBy(userId)
                .status(TaskStatus.TODO)
                .build();

        Task saved = taskRepository.save(task);
        recalculateProgress(project);
        return saved;
    }

    public List<Task> getProjectTasks(UUID projectId, UUID userId) {
        Project project = getProjectById(projectId, userId);
        return taskRepository.findByProjectIdOrderByPositionAscCreatedAtAsc(project.getId());
    }

    /**
     * Urutan tahap Flow, ditegakkan di sini karena sampai sekarang hanya ditegakkan
     * di browser.
     *
     * <p>`nextStageOf()` di ProjectDetailPage.tsx memaksa TODO -> IN_PROGRESS ->
     * IN_REVIEW -> DONE, tapi metode ini menerima status apa pun: satu panggilan
     * `PATCH /tasks/{id}/status` dengan status=DONE pada Flow yang masih TODO lolos,
     * tahap review terlewat, dan recalculateProgress langsung menaikkan proyek ke 100%.
     * Aturan alur yang hanya hidup di komponen React bukan aturan.
     *
     * <p>Mundur sengaja diizinkan ke tahap mana pun sebelumnya: itulah cara hasil
     * review ditolak dan Flow yang terlanjur DONE dibuka lagi. Tanpa itu, Flow yang
     * gagal review terkunci selamanya — tidak ada kontrol di UI untuk mengembalikannya.
     */
    // Package-private dan statis supaya test memanggil aturan yang sebenarnya dipakai,
    // bukan salinannya. Test yang mencerminkan rumus bisa tetap hijau saat aslinya berubah.
    static void validateStageTransition(TaskStatus from, TaskStatus to) {
        if (from == to) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Flow sudah berada di tahap " + to + ".");
        }
        // Maju lebih dari satu tahap ditolak; mundur (ordinal lebih kecil) selalu boleh.
        if (to.ordinal() > from.ordinal() + 1) {
            TaskStatus next = TaskStatus.values()[from.ordinal() + 1];
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tahap tidak boleh dilompati. Dari " + from + " hanya bisa lanjut ke " + next + ".");
        }
    }

    @Transactional
    public Task updateTaskStatus(UUID taskId, UUID userId, TaskStatus status, String reason) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task status is required");
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow tidak ditemukan."));
        Project project = projectRepository.findById(task.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan."));
        enforceProjectWritable(project, userId, "mengubah status task");
        validateStageTransition(task.getStatus(), status);

        task.setStatus(status);
        if (status == TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
        } else {
            task.setCompletedAt(null);
        }

        Task saved = taskRepository.save(task);

        // Jejak alasan ditulis di metode yang sama, bukan lewat panggilan HTTP kedua
        // dari frontend. Satu @Transactional: status dan alasannya tersimpan bersama
        // atau sama-sama gagal. Sebelumnya panggilan kedua bisa gagal sendiri dan
        // meninggalkan perpindahan tahap tanpa catatan apa pun.
        commentRepository.save(Comment.builder()
                .projectId(project.getId())
                .entityType("TASK")
                .entityId(task.getId())
                .authorId(userId)
                .content("Flow dilanjutkan ke " + status + ". Alasan: " + reason.trim())
                .internal(false)
                .build());

        recalculateProgress(project);
        return saved;
    }

    @Transactional
    public Task updateTask(UUID taskId, UUID userId, UpdateTaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow tidak ditemukan."));
        Project project = projectRepository.findById(task.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan."));
        enforceProjectWritable(project, userId, "mengubah task");

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            task.setTitle(validateFlowTitle(request.getTitle()));
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }

        // Flag terpisah supaya "tidak diubah" (null) bisa dibedakan dari
        // "kosongkan" — tanpa itu penerima tugas tidak akan pernah bisa dilepas.
        if (request.isClearAssignee()) {
            task.setAssignedTo(null);
        } else if (request.getAssignedTo() != null) {
            enforceAssigneeIsMember(project, request.getAssignedTo());
            task.setAssignedTo(request.getAssignedTo());
        }

        if (request.isClearDueDate()) {
            task.setDueDate(null);
        } else if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }

        if (request.isClearStartDate()) {
            task.setStartDate(null);
        } else if (request.getStartDate() != null) {
            task.setStartDate(request.getStartDate());
        }

        if (request.isClearEstimate()) {
            task.setEstimatedHours(null);
        } else if (request.getEstimatedHours() != null) {
            task.setEstimatedHours(request.getEstimatedHours());
        }

        validateTaskSchedule(task.getStartDate(), task.getDueDate(), task.getEstimatedHours(),
                project.getStartDate(), project.getTargetDate());

        return taskRepository.save(task);
    }

    /** Menyempitkan jadwal proyek bisa membuat Flow yang sudah ada jatuh di luar jendela.
     *  Tolak dengan jumlahnya, supaya user membetulkan Flow-nya dulu daripada diam-diam
     *  meninggalkan data yang tidak konsisten. */
    private void enforceNoFlowOutsideWindow(UUID projectId, java.time.LocalDate start,
                                            java.time.LocalDate target) {
        if (start == null && target == null) {
            return;
        }
        long outside = taskRepository.findByProjectIdOrderByPositionAscCreatedAtAsc(projectId).stream()
                .filter(task -> isOutsideWindow(task.getStartDate(), start, target)
                        || isOutsideWindow(task.getDueDate(), start, target))
                .count();
        if (outside > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    outside + " Flow berada di luar jadwal proyek yang baru. Sesuaikan tanggal Flow tersebut dulu.");
        }
    }

    private static boolean isOutsideWindow(java.time.LocalDate date, java.time.LocalDate start,
                                           java.time.LocalDate target) {
        if (date == null) {
            return false;
        }
        return (start != null && date.isBefore(start)) || (target != null && date.isAfter(target));
    }

    static void validateTaskSchedule(java.time.LocalDate startDate, java.time.LocalDate dueDate,
                                     java.math.BigDecimal estimatedHours) {
        validateTaskSchedule(startDate, dueDate, estimatedHours, null, null);
    }

    /** Satu-satunya tempat jadwal Flow divalidasi; createTask dan updateTask sama-sama
     *  lewat sini. projectStart/projectTarget boleh null (proyek tanpa jadwal) — kalau
     *  diisi, Flow tidak boleh keluar dari jendela proyek, karena Flow yang mulai
     *  sebelum proyeknya atau selesai setelah target membuat progres dan beban kerja
     *  di dashboard tidak bisa dipercaya. */
    static void validateTaskSchedule(java.time.LocalDate startDate, java.time.LocalDate dueDate,
                                     java.math.BigDecimal estimatedHours,
                                     java.time.LocalDate projectStart, java.time.LocalDate projectTarget) {
        if (estimatedHours != null && estimatedHours.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estimasi jam harus lebih dari 0.");
        }
        if (estimatedHours != null && estimatedHours.compareTo(MAX_ESTIMATED_HOURS) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estimasi jam maksimal " + MAX_ESTIMATED_HOURS + " jam.");
        }
        if (startDate != null && dueDate != null && startDate.isAfter(dueDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal mulai Flow harus sebelum atau sama dengan target selesai.");
        }
        if (projectStart != null && startDate != null && startDate.isBefore(projectStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal mulai Flow tidak boleh sebelum tanggal mulai proyek (" + projectStart + ").");
        }
        if (projectTarget != null && dueDate != null && dueDate.isAfter(projectTarget)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target selesai Flow tidak boleh melewati target proyek (" + projectTarget + ").");
        }
        // Flow tanpa tanggal mulai tetap dicek ujungnya, supaya due date liar tidak lolos
        // lewat celah "startDate null".
        if (projectStart != null && dueDate != null && dueDate.isBefore(projectStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target selesai Flow tidak boleh sebelum tanggal mulai proyek (" + projectStart + ").");
        }
        if (projectTarget != null && startDate != null && startDate.isAfter(projectTarget)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal mulai Flow tidak boleh melewati target proyek (" + projectTarget + ").");
        }
    }

    /** Kolom demo_url VARCHAR(1000). Skema dicek di server juga, bukan hanya lewat
     *  safeDemoUrl di frontend: URL ini masuk ke src sebuah iframe, dan klien lain
     *  (mobile, integrasi) tidak lewat pengecekan frontend itu. */
    static final int MAX_DEMO_URL_LENGTH = 1000;

    static String validateDemoUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            return null;
        }
        if (url.length() > MAX_DEMO_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL demo maksimal " + MAX_DEMO_URL_LENGTH + " karakter.");
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL demo harus diawali http:// atau https://.");
        }
        return url;
    }

    /** Dipakai createTask dan updateTask. Tanpa batas panjang di jalur update, judul
     *  di atas 255 karakter lolos sampai driver dan muncul sebagai 500, bukan 400. */
    static String validateFlowTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nama Flow wajib diisi.");
        }
        if (title.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nama Flow maksimal " + MAX_NAME_LENGTH + " karakter.");
        }
        return title;
    }

    /** Dipakai createProject dan updateProjectDetails. Sebelumnya hanya update yang
     *  mengecek, jadi proyek bisa dibuat dengan target sebelum tanggal mulai. */
    static String validateProjectSchedule(String rawName, java.time.LocalDate startDate,
                                          java.time.LocalDate targetDate) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nama proyek wajib diisi.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nama proyek maksimal " + MAX_NAME_LENGTH + " karakter.");
        }
        if (startDate != null && targetDate != null && startDate.isAfter(targetDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal mulai proyek harus sebelum atau sama dengan target selesai.");
        }
        return name;
    }

    /** Tugas hanya boleh diberikan ke orang yang punya akses ke proyek ini; tanpa cek
     *  ini UUID sembarang bisa disimpan dan muncul sebagai penerima tugas hantu.
     *  Pakai peran efektif, supaya kolaborator khusus-proyek juga bisa di-assign. */
    private void enforceAssigneeIsMember(Project project, UUID assigneeId) {
        if (effectiveRole(project, assigneeId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Penerima tugas tidak punya akses ke proyek ini.");
        }
    }

    // ==================== MILESTONES & REVISIONS ====================

    public List<Milestone> getProjectMilestones(UUID projectId, UUID userId) {
        Project project = getProjectById(projectId, userId);
        return milestoneRepository.findByProjectId(project.getId());
    }

    // ==================== COMMENTS ====================

    @Transactional
    public CommentDto addComment(UUID projectId, UUID userId, String entityType, UUID entityId, String content, boolean internal) {
        Project project = getProjectById(projectId, userId);

        String normalizedType = normalizeEntityType(entityType);
        UUID targetId = "PROJECT".equals(normalizedType) ? project.getId() : entityId;

        if (!"PROJECT".equals(normalizedType) && entityId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, normalizedType + " id wajib diisi.");
        }
        // Tanpa cek ini, id task/revisi dari proyek lain bisa dipakai untuk menyelipkan
        // komentar ke proyek yang tidak boleh diakses pengirim.
        enforceEntityBelongsToProject(normalizedType, targetId, project.getId());

        boolean isClient = "CLIENT".equals(effectiveRole(project, userId));
        if (isClient && internal) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CLIENT tidak dapat membuat komentar internal.");
        }

        Comment comment = Comment.builder()
                .projectId(project.getId())
                .entityType(normalizedType)
                .entityId(targetId)
                .authorId(userId)
                .content(content)
                .internal(internal)
                .build();
        commentRepository.save(comment);

        return toCommentDto(comment);
    }

    /** Batas jumlah komentar yang dikembalikan sekali panggil.
     *
     *  <p>Komentar tumbuh tanpa batas: setiap perpindahan tahap Flow menambah satu
     *  otomatis, jadi proyek yang berumur panjang mengumpulkan ribuan. Mengembalikan
     *  semuanya baik-baik saja di proyek baru dan menjatuhkan service di proyek lama —
     *  persis kelas masalah yang tidak terlihat sampai produksinya ramai. */
    static final int MAX_COMMENTS_PER_REQUEST = 200;

    public List<CommentDto> getComments(UUID projectId, UUID userId, String entityType, UUID entityId) {
        return getComments(projectId, userId, entityType, entityId, MAX_COMMENTS_PER_REQUEST);
    }

    public List<CommentDto> getComments(UUID projectId, UUID userId, String entityType, UUID entityId, int limit) {
        Project project = getProjectById(projectId, userId);

        String normalizedType = normalizeEntityType(entityType);
        UUID targetId = "PROJECT".equals(normalizedType) ? project.getId() : entityId;

        if (!"PROJECT".equals(normalizedType) && entityId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, normalizedType + " id wajib diisi.");
        }
        enforceEntityBelongsToProject(normalizedType, targetId, project.getId());

        // Diambil dari yang terbaru lewat LIMIT di SQL, lalu dibalik ke urutan
        // kronologis: percakapan dibaca dari atas, tapi yang relevan ada di bawah.
        // Memotong di memori tetap menarik seluruh percakapan dari database dulu.
        int safeLimit = Math.max(1, Math.min(limit, MAX_COMMENTS_PER_REQUEST));
        Pageable page = PageRequest.of(0, safeLimit);

        boolean isClient = "CLIENT".equals(effectiveRole(project, userId));
        List<Comment> comments = isClient
                ? commentRepository.findByProjectIdAndEntityTypeAndEntityIdAndInternalFalseOrderByCreatedAtDesc(
                        project.getId(), normalizedType, targetId, page)
                : commentRepository.findByProjectIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        project.getId(), normalizedType, targetId, page);

        List<Comment> chronological = new java.util.ArrayList<>(comments);
        java.util.Collections.reverse(chronological);
        return chronological.stream().map(this::toCommentDto).toList();
    }

    private static String normalizeEntityType(String entityType) {
        if ("TASK".equalsIgnoreCase(entityType)) return "TASK";
        return "PROJECT";
    }

    /** Memastikan task yang dikomentari memang milik proyek ini. */
    private void enforceEntityBelongsToProject(String entityType, UUID entityId, UUID projectId) {
        if ("PROJECT".equals(entityType)) return;

        List<UUID> owners = jdbcTemplate.query(
                "SELECT project_id FROM tasks WHERE id = ?",
                (rs, rowNum) -> (UUID) rs.getObject("project_id"), entityId);

        if (owners.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, entityType + " tidak ditemukan.");
        }
        if (!projectId.equals(owners.get(0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, entityType + " bukan milik proyek ini.");
        }
    }

    private CommentDto toCommentDto(Comment comment) {
        String authorName = "User";
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT full_name FROM users WHERE id = ?",
                    (rs, rowNum) -> rs.getString("full_name"), comment.getAuthorId());
            if (!names.isEmpty()) authorName = names.get(0);
        } catch (Exception ignored) {}

        return CommentDto.builder()
                .id(comment.getId())
                .projectId(comment.getProjectId())
                .entityType(comment.getEntityType())
                .entityId(comment.getEntityId())
                .authorId(comment.getAuthorId())
                .authorName(authorName)
                .content(comment.getContent())
                .internal(comment.isInternal())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
