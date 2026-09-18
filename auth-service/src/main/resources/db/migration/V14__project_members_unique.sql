-- V14__project_members_unique.sql
--
-- project_members tidak pernah punya constraint unik pada (project_id, user_id):
-- hanya PRIMARY KEY di kolom id, plus dua indeks biasa yang tidak unik.
--
-- Tapi WorkspaceService.acceptInvitation menulis dengan
--
--     INSERT INTO project_members (...) VALUES (...)
--     ON CONFLICT (project_id, user_id) DO NOTHING
--
-- dan Postgres menolak ON CONFLICT yang tidak punya constraint pasangannya:
--
--     ERROR: there is no unique or exclusion constraint matching
--            the ON CONFLICT specification
--
-- Artinya MENERIMA UNDANGAN PROYEK selalu gagal dengan 500. Bukan kadang-kadang —
-- setiap kali, sejak baris itu ditulis. Cacatnya tidak kelihatan dari membaca kode:
-- pernyataannya benar secara sintaks, dan niatnya jelas benar. Yang menemukannya
-- adalah menjalankan INSERT itu sungguhan.
--
-- Constraint ini juga yang seharusnya ada sejak awal terlepas dari ON CONFLICT:
-- tanpa itu satu orang bisa punya banyak baris keanggotaan untuk proyek yang sama,
-- masing-masing dengan peran berbeda, dan tidak ada aturan yang menentukan mana
-- yang menang.
--
-- Diperiksa lebih dulu di database yang berjalan: nol pasangan duplikat, jadi
-- constraint-nya bisa dipasang tanpa membersihkan data.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_project_members_project_user'
    ) THEN
        ALTER TABLE project_members
            ADD CONSTRAINT uq_project_members_project_user UNIQUE (project_id, user_id);
    END IF;
END $$;
