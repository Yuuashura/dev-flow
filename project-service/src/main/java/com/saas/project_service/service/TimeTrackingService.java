package com.saas.project_service.service;

import com.saas.project_service.dto.MemberWorkloadDto;
import com.saas.project_service.dto.ProjectDashboardResponse;
import com.saas.project_service.dto.TimeEntryRequest;
import com.saas.project_service.dto.TimeEntryResponse;
import com.saas.project_service.entity.*;
import com.saas.project_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimeTrackingService {

    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public TimeEntryResponse createTimeEntry(UUID projectId, UUID userId, TimeEntryRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan."));

        // Verify user is member of project
        String role = getMemberRole(project.getId(), userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota proyek ini.");
        }

        resolveTask(projectId, request.getTaskId());
        String entryType = validateTimeEntry(request, project);

        TimeEntry entry = TimeEntry.builder()
                .projectId(projectId)
                .taskId(request.getTaskId())
                .userId(userId)
                .entryDate(request.getEntryDate())
                .hours(request.getHours())
                .description(request.getDescription())
                .entryType(entryType)
                .featureName(trimToNull(request.getFeatureName()))
                .build();

        TimeEntry saved = timeEntryRepository.save(entry);
        return toResponse(saved);
    }

    public Page<TimeEntryResponse> getTimeEntries(UUID projectId, UUID userId, Pageable pageable) {
        String role = getMemberRole(projectId, userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota proyek ini.");
        }
        return timeEntryRepository.findByProjectIdOrderByEntryDateDescCreatedAtDesc(projectId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public TimeEntryResponse updateTimeEntry(UUID projectId, UUID userId, UUID entryId, TimeEntryRequest request) {
        String role = getMemberRole(projectId, userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota proyek ini.");
        }

        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entri waktu tidak ditemukan."));

        if (!entry.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entri waktu tersebut bukan milik proyek ini.");
        }

        // Only owner or same user can edit
        if (!entry.getUserId().equals(userId) && !"OWNER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya pemilik entri atau OWNER proyek yang dapat mengubahnya.");
        }

        // Jalur update dulu mengambil taskId mentah dari body tanpa cek "task ini milik
        // proyek ini" yang dilakukan createTimeEntry, jadi entri waktu bisa dipindahkan
        // ke Flow milik proyek lain. Kedua jalur sekarang lewat helper yang sama.
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan."));
        resolveTask(projectId, request.getTaskId());
        String entryType = validateTimeEntry(request, project);

        entry.setTaskId(request.getTaskId());
        entry.setEntryDate(request.getEntryDate());
        entry.setHours(request.getHours());
        entry.setDescription(request.getDescription());
        entry.setEntryType(entryType);
        entry.setFeatureName(trimToNull(request.getFeatureName()));

        return toResponse(timeEntryRepository.save(entry));
    }

    @Transactional
    public void deleteTimeEntry(UUID projectId, UUID userId, UUID entryId) {
        String role = getMemberRole(projectId, userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota proyek ini.");
        }

        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entri waktu tidak ditemukan."));

        if (!entry.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entri waktu tersebut bukan milik proyek ini.");
        }

        if (!entry.getUserId().equals(userId) && !"OWNER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya pemilik entri atau OWNER proyek yang dapat menghapusnya.");
        }

        timeEntryRepository.delete(entry);
    }

    public ProjectDashboardResponse getDashboard(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan."));
        if (getMemberRole(project.getId(), userId) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota proyek ini.");
        }

        // Fetch recent entries
        List<TimeEntry> recentEntries = timeEntryRepository.findByProjectIdOrderByEntryDateDescCreatedAtDesc(projectId);
        List<TimeEntryResponse> recentEntryResponses = recentEntries.stream()
                .limit(10)
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Calculate time summaries
        BigDecimal totalHours = timeEntryRepository.sumHoursByProject(projectId);
        
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        BigDecimal hoursThisWeek = timeEntryRepository.sumHoursByProjectAndDateRange(projectId, weekStart, weekEnd);
        
        BigDecimal hoursToday = timeEntryRepository.sumHoursByProjectAndDateRange(projectId, LocalDate.now(), LocalDate.now());

// Task counts
        long taskCount = taskRepository.countByProjectId(projectId);
        long doneTaskCount = taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.DONE);
        long overdueTaskCount = taskRepository.countByProjectIdAndDueDateBeforeAndStatusNot(projectId, LocalDate.now(), TaskStatus.DONE);

        // Member workloads
        List<MemberWorkloadDto> members = getMemberWorkloads(projectId);

        return ProjectDashboardResponse.builder()
                .project(projectRepository.findById(projectId).orElseThrow())
                .progressPercent(project.getProgressPercent())
                .totalHours(totalHours)
                .hoursThisWeek(hoursThisWeek)
                .hoursToday(hoursToday)
                .memberCount((long) getMembers(projectId).size())
                .taskCount(taskCount)
                .doneTaskCount(doneTaskCount)
                .overdueTaskCount(overdueTaskCount)
                .recentEntries(recentEntryResponses)
                .members(members)
                .build();
    }

    public List<TimeEntryResponse> getRecentEntries(UUID projectId, UUID userId, int limit) {
        String role = getMemberRole(projectId, userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota proyek ini.");
        }
        // limit datang langsung dari query param. Tanpa clamp, limit=0 atau negatif
        // membuat Stream.limit melempar IllegalArgumentException, dan nilai besar
        // memaksa seluruh entri proyek dipetakan ke DTO hanya untuk dibuang.
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECENT_ENTRIES));
        // Varian Pageable sudah ada di repository sejak lama tapi tidak pernah dipanggil:
        // batasnya diterapkan setelah seluruh entri proyek terbaca. Sekarang LIMIT-nya
        // ada di SQL, jadi baris yang dibuang tidak pernah meninggalkan database.
        return timeEntryRepository
                .findByProjectIdOrderByEntryDateDescCreatedAtDesc(projectId, PageRequest.of(0, safeLimit))
                .getContent().stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ==================== VALIDASI ====================

    /** Nilai yang diizinkan untuk entry_type.
     *
     *  <p>V8 mendeklarasikan CHECK constraint untuk ini, tapi migrasi auth-service tidak
     *  pernah dijalankan — Flyway autoconfig tidak aktif di sana, dan tabelnya dibuat
     *  ddl-auto dari entity, tanpa constraint. Jadi pengecekan di bawah ini bukan
     *  lapisan kedua di atas database: ia satu-satunya yang menjaga. */
    static final java.util.Set<String> ENTRY_TYPES =
            java.util.Set.of("FEATURE", "BUG", "REVIEW", "MEETING", "OTHER");

    /** V8 mendeklarasikan hours NUMERIC(4,2) CHECK (hours > 0 AND hours <= 24), tapi
     *  migrasi itu tidak pernah berjalan (lihat catatan pada ENTRY_TYPES), jadi batas ini
     *  tidak di-backstop database. */
    static final BigDecimal MAX_HOURS_PER_ENTRY = new BigDecimal("24");

    /** feature_name VARCHAR(255). */
    static final int MAX_FEATURE_NAME_LENGTH = 255;

    static final int MAX_RECENT_ENTRIES = 200;

    /** Entri lebih tua dari ini hampir pasti salah ketik tahun, dan merusak skala
     *  grafik beban kerja karena sumbu waktunya melar bertahun-tahun. */
    static final int MAX_BACKDATE_DAYS = 365;

    /**
     * Satu-satunya tempat isi TimeEntryRequest divalidasi; createTimeEntry dan
     * updateTimeEntry sama-sama lewat sini. Mengembalikan entryType yang sudah
     * dinormalkan.
     *
     * <p>Semua batas di sini mencerminkan apa yang DIMAKSUDKAN
     * V8__create_time_entries.sql. Migrasi itu tidak pernah dijalankan, jadi constraint
     * tersebut tidak ada di database dan kode ini satu-satunya penjaganya.
     * Sebelumnya tidak ada satu pun yang dicek di kode:
     * anotasi bean validation pada DTO tidak pernah jalan karena controller memakai
     * {@code @RequestBody} tanpa {@code @Valid}, jadi Postgres yang menolaknya — sebagai
     * 500, bukan 400 yang bisa ditampilkan.
     */
    static String validateTimeEntry(TimeEntryRequest request, Project project) {
        if (request.getEntryType() == null || request.getEntryType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jenis pekerjaan wajib diisi.");
        }
        String entryType = request.getEntryType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!ENTRY_TYPES.contains(entryType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Jenis pekerjaan harus salah satu dari: FEATURE, BUG, REVIEW, MEETING, OTHER.");
        }

        BigDecimal hours = request.getHours();
        if (hours == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Durasi jam wajib diisi.");
        }
        if (hours.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Durasi jam harus lebih dari 0.");
        }
        if (hours.compareTo(MAX_HOURS_PER_ENTRY) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Durasi satu entri maksimal " + MAX_HOURS_PER_ENTRY + " jam.");
        }

        String featureName = trimToNull(request.getFeatureName());
        if (featureName != null && featureName.length() > MAX_FEATURE_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nama fitur maksimal " + MAX_FEATURE_NAME_LENGTH + " karakter.");
        }

        validateEntryDate(request.getEntryDate(),
                project != null ? project.getStartDate() : null,
                project != null ? project.getTargetDate() : null,
                LocalDate.now());

        return entryType;
    }

    /**
     * Tanggal entri dibatasi jendela proyek, sejalan dengan aturan yang sudah berlaku
     * untuk Flow. Tanggal hari ini dipisahkan sebagai parameter supaya bisa diuji.
     */
    static void validateEntryDate(LocalDate entryDate, LocalDate projectStart,
                                  LocalDate projectTarget, LocalDate today) {
        if (entryDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tanggal entri wajib diisi.");
        }
        if (entryDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal entri tidak boleh di masa depan.");
        }
        if (entryDate.isBefore(today.minusDays(MAX_BACKDATE_DAYS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal entri tidak boleh lebih dari " + MAX_BACKDATE_DAYS + " hari ke belakang.");
        }
        if (projectStart != null && entryDate.isBefore(projectStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal entri tidak boleh sebelum proyek dimulai (" + projectStart + ").");
        }
        if (projectTarget != null && entryDate.isAfter(projectTarget)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal entri tidak boleh melewati target proyek (" + projectTarget + ").");
        }
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Flow opsional pada entri waktu, tapi kalau diisi harus milik proyek ini. */
    private Task resolveTask(UUID projectId, UUID taskId) {
        if (taskId == null) {
            return null;
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow tidak ditemukan."));
        if (!task.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Flow tersebut bukan milik proyek ini.");
        }
        return task;
    }

    public List<TimeEntryResponse> getTimeEntriesByUser(UUID projectId, UUID targetUserId, UUID userId) {
        String role = getMemberRole(projectId, userId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota proyek ini.");
        }
        return timeEntryRepository.findByProjectIdAndUserIdOrderByEntryDateDescCreatedAtDesc(projectId, targetUserId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private List<MemberWorkloadDto> getMemberWorkloads(UUID projectId) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<ProjectMember> members = projectMemberRepository.findByProjectIdAndStatus(projectId, "ACTIVE");

        return members.stream().map(member -> {
            String name = getUserFullName(member.getUserId());
            String email = getUserEmail(member.getUserId());
            BigDecimal hoursThisWeek = timeEntryRepository.sumHoursByProjectAndUserAndDateRange(
                    projectId, member.getUserId(), 
                    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            if (hoursThisWeek == null) hoursThisWeek = BigDecimal.ZERO;

            BigDecimal totalHours = timeEntryRepository.sumHoursByProjectAndUser(projectId, member.getUserId());
            if (totalHours == null) totalHours = BigDecimal.ZERO;

            long tasksAssigned = taskRepository.countByProjectIdAndAssignedTo(projectId, member.getUserId());
            long tasksDone = taskRepository.countByProjectIdAndAssignedToAndStatus(
                    projectId, member.getUserId(), TaskStatus.DONE);

            return MemberWorkloadDto.builder()
                    .userId(member.getUserId())
                    .fullName(name)
                    .email(getUserEmail(member.getUserId()))
                    .role(member.getRole())
                    .hoursThisWeek(hoursThisWeek)
                    .totalHours(totalHours)
                    .tasksAssigned(tasksAssigned)
                    .tasksDone(tasksDone)
                    .build();
        }).collect(Collectors.toList());
    }

    public TimeEntryResponse toResponse(TimeEntry entry) {
        String taskTitle = null;
        if (entry.getTaskId() != null) {
            Optional<Task> task = taskRepository.findById(entry.getTaskId());
            taskTitle = task.map(Task::getTitle).orElse(null);
        }
        String userName = getUserFullName(entry.getUserId());

        return TimeEntryResponse.builder()
                .id(entry.getId())
                .projectId(entry.getProjectId())
                .taskId(entry.getTaskId())
                .taskTitle(taskTitle)
                .userId(entry.getUserId())
                .userName(userName)
                .entryDate(entry.getEntryDate())
                .hours(entry.getHours())
                .description(entry.getDescription())
                .entryType(entry.getEntryType())
                .featureName(entry.getFeatureName())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    /**
     * Role efektif: project_members dulu, lalu fallback ke workspace_members.
     * Satu-satunya sumber jawaban di kelas ini — sebelumnya getDashboard pakai fallback
     * workspace sementara method lain hanya melihat project_members, jadi anggota
     * level workspace bisa membuka dashboard tapi selalu ditolak di setiap time entry.
     * Sama seperti ProjectService.effectiveRole.
     */
    private String getMemberRole(UUID projectId, UUID userId) {
        List<String> projectRoles = jdbcTemplate.query(
                "SELECT role FROM project_members WHERE project_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("role"), projectId, userId);
        if (!projectRoles.isEmpty()) return projectRoles.get(0);

        List<String> workspaceRoles = jdbcTemplate.query(
                "SELECT wm.role FROM workspace_members wm " +
                        "JOIN projects p ON p.workspace_id = wm.workspace_id " +
                        "WHERE p.id = ? AND wm.user_id = ? AND wm.status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("role"), projectId, userId);
        return workspaceRoles.isEmpty() ? null : workspaceRoles.get(0);
    }

    private List<ProjectMember> getMembers(UUID projectId) {
        return projectMemberRepository.findByProjectIdAndStatus(projectId, "ACTIVE");
    }

    private String getUserFullName(UUID userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String getUserEmail(UUID userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return null;
        }
    }
}
