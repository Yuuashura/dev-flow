-- Akses per proyek. Sebelum ini keanggotaan hanya ada di level workspace, jadi
-- mengundang seseorang memberi dia akses ke SEMUA proyek di workspace itu.
--
-- Pengecekan akses berlapis: project_members dulu, workspace_members sebagai
-- cadangan. Anggota workspace yang sudah ada tetap bisa membuka semua proyek —
-- tidak ada yang perlu di-backfill dan tidak ada akses yang hilang.

CREATE TABLE IF NOT EXISTS project_members (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users(id),
    role          VARCHAR(30) NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    invited_by    UUID        REFERENCES users(id),
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_pm_user ON project_members (user_id);
CREATE INDEX IF NOT EXISTS idx_pm_project ON project_members (project_id);

-- Undangan dipakai ulang untuk dua cakupan. project_id NULL = undangan workspace
-- (perilaku lama, tetap jalan); terisi = undangan yang hanya memberi akses ke satu
-- proyek. Satu tabel berarti satu alur terima undangan, bukan dua yang harus
-- dijaga tetap sinkron.
ALTER TABLE invitations ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES projects(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_inv_project ON invitations (project_id) WHERE project_id IS NOT NULL;
