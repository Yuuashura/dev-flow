-- V11__restore_indexes_and_checks.sql
--
-- Migrasi V1-V10 tidak pernah berjalan: Spring Boot 4 memindahkan autoconfiguration
-- Flyway ke modul spring-boot-flyway, dan service ini hanya punya flyway-core, jadi
-- spring.flyway.* tidak dibaca siapa pun. Tabelnya tetap ada karena ddl-auto
-- membangunnya dari entity — tapi ddl-auto hanya tahu entity, bukan SQL. Semua yang
-- hanya hidup di berkas migrasi tidak pernah terbentuk.
--
-- Berkas ini membawanya kembali. Flyway di-baseline pada versi 10, jadi V1-V10
-- dianggap sudah diterapkan dan tidak akan dijalankan ulang (V1 dan V2 tidak punya
-- IF NOT EXISTS sama sekali dan akan gagal).
--
-- Setiap pernyataan di sini idempoten, supaya aman dijalankan pada database yang
-- sudah berisi sebagian objeknya.

-- ==================== INDEX ====================
-- Tidak satu pun dari ini ada sebelum V11. Query-nya tetap benar tanpa index,
-- hanya melakukan sequential scan.

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_global_role ON users(global_role);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);

CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user_id ON refresh_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires_at ON refresh_sessions(expires_at);

CREATE INDEX IF NOT EXISTS idx_oauth_states_expires_at ON oauth_states(expires_at);

CREATE INDEX IF NOT EXISTS idx_workspaces_owner ON workspaces(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_workspaces_slug ON workspaces(slug);

CREATE INDEX IF NOT EXISTS idx_wm_workspace ON workspace_members(workspace_id);
CREATE INDEX IF NOT EXISTS idx_wm_user ON workspace_members(user_id);

CREATE INDEX IF NOT EXISTS idx_projects_workspace ON projects(workspace_id);

CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON tasks(assigned_to);

CREATE INDEX IF NOT EXISTS idx_comments_project_entity ON comments(project_id, entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_pm_project ON project_members(project_id);
CREATE INDEX IF NOT EXISTS idx_pm_user ON project_members(user_id);

CREATE INDEX IF NOT EXISTS idx_inv_project ON invitations(project_id);

CREATE INDEX IF NOT EXISTS idx_time_entries_project_date ON time_entries(project_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_time_entries_user_date ON time_entries(user_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_time_entries_task ON time_entries(task_id);
CREATE INDEX IF NOT EXISTS idx_time_entries_project_user ON time_entries(project_id, user_id);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_status ON user_notifications(user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_notifications_user_type ON user_notifications(user_id, type);
CREATE INDEX IF NOT EXISTS idx_user_notifications_expires_at ON user_notifications(expires_at);

-- ==================== CHECK CONSTRAINT ====================
-- Hibernate membuat check untuk kolom enum, tapi tidak untuk aturan yang ditulis
-- tangan di SQL. Empat aturan di bawah karena itu tidak pernah ditegakkan database.
-- Kode aplikasi sudah memvalidasi semuanya (TimeTrackingService.validateTimeEntry,
-- ProjectService.validateTaskSchedule); ini lapisan keduanya.
--
-- NOT VALID: baris lama tidak diperiksa, jadi data yang sudah terlanjur melanggar
-- tidak menggagalkan migrasi. Penulisan baru tetap diperiksa penuh.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_time_entries_hours') THEN
        ALTER TABLE time_entries
            ADD CONSTRAINT chk_time_entries_hours
            CHECK (hours > 0 AND hours <= 24) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_time_entries_entry_type') THEN
        ALTER TABLE time_entries
            ADD CONSTRAINT chk_time_entries_entry_type
            CHECK (entry_type IN ('FEATURE','BUG','REVIEW','MEETING','OTHER')) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_tasks_estimated_hours') THEN
        ALTER TABLE tasks
            ADD CONSTRAINT chk_tasks_estimated_hours
            CHECK (estimated_hours IS NULL OR estimated_hours > 0) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_tasks_date_order') THEN
        ALTER TABLE tasks
            ADD CONSTRAINT chk_tasks_date_order
            CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date) NOT VALID;
    END IF;
END $$;

-- Catatan: V2 menyemai akun SUPER_ADMIN dengan password yang diketahui publik
-- ('Admin123!') dan hash-nya ter-commit di repo. Itu sengaja TIDAK dibawa kembali
-- ke sini. Untuk memberi akses admin, naikkan akun yang sudah ada:
--
--   UPDATE users SET global_role = 'SUPER_ADMIN' WHERE email = '<email anda>';
