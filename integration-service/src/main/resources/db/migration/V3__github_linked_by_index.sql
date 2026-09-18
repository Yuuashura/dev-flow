-- V3__github_linked_by_index.sql
--
-- V2 tidak pernah berjalan: service ini tidak punya Flyway sama sekali sampai
-- sekarang. Kolom projects.github_linked_by tetap ada karena ddl-auto membangunnya
-- dari entity, tapi index parsial milik V2 tidak — ddl-auto hanya tahu entity.
--
-- Flyway di-baseline pada versi 2, jadi V2 dianggap sudah diterapkan. Index-nya
-- dibawa kembali di sini.

CREATE INDEX IF NOT EXISTS idx_projects_github_linked_by
    ON projects(github_linked_by)
    WHERE github_linked_by IS NOT NULL;
