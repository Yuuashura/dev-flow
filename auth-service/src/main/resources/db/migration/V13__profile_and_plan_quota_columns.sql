-- V13__profile_and_plan_quota_columns.sql
--
-- Sebelas kolom yang dipakai entity tapi tidak pernah ditulis migrasi mana pun.
--
-- Kolom ini ada di database pengembangan hanya karena `ddl-auto: update` dulu
-- menambahkannya diam-diam dari entity. Begitu service beralih ke `validate`,
-- database yang sudah berjalan tetap lolos — kolomnya terlanjur ada di sana.
-- Lingkungan BARU tidak: menjalankan V1–V12 dari nol menghasilkan 25 tabel, lalu
-- startup mati dengan
--
--     Schema validation: missing column [bio] in table [users]
--
-- Artinya deploy baru tidak bisa hidup sama sekali. Cacat ini tidak terlihat dari
-- membaca kode maupun dari memeriksa database yang sedang jalan — yang menemukannya
-- adalah menjalankan riwayat migrasi terhadap database kosong dan membandingkan
-- hasilnya kolom per kolom dengan database yang hidup.
--
-- Bentuk di bawah disalin persis dari database yang berjalan, jadi menjalankannya
-- di sana adalah no-op. Semua nullable: ini kolom profil opsional dan kuota paket,
-- dan baris yang sudah ada tidak punya nilai untuk diisi.

ALTER TABLE users ADD COLUMN IF NOT EXISTS bio          VARCHAR(1000);
ALTER TABLE users ADD COLUMN IF NOT EXISTS city         VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS company_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title    VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(30);

-- Kuota paket. Nilainya dibaca saat menegakkan batas seat dan batas proyek, jadi
-- paket tanpa baris kuota berarti batasnya tidak pernah berlaku.
ALTER TABLE plans ADD COLUMN IF NOT EXISTS max_workspaces             INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS max_projects_per_workspace INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS max_members_per_workspace  INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS max_storage_gb             INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS milestones_per_project     INTEGER;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS github_repos               INTEGER;
