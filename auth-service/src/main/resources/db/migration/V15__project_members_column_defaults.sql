-- V15__project_members_column_defaults.sql
--
-- Melanjutkan V14. Menerima undangan proyek gagal karena EMPAT hal sekaligus, bukan
-- satu; V14 baru menutup yang keempat.
--
-- WorkspaceService.acceptInvitation menulis dengan JDBC mentah:
--
--     INSERT INTO project_members (project_id, user_id, role, status, invited_by)
--     VALUES (?, ?, ?, 'ACTIVE', ?) ON CONFLICT (project_id, user_id) DO NOTHING
--
-- Sementara tabelnya menuntut empat hal yang tidak diberikan pernyataan itu:
--
--   1. id          NOT NULL, tanpa default
--   2. joined_at   NOT NULL, tanpa default
--   3. updated_at  NOT NULL, tanpa default
--   4. constraint unik pasangan ON CONFLICT  -> ditutup V14
--
-- Ketiga kolom pertama ada karena entity Hibernate membuatnya lewat @Id,
-- @CreationTimestamp, dan @UpdateTimestamp — anotasi yang mengisi nilainya saat
-- menyimpan lewat JPA, dan tidak berlaku sama sekali untuk INSERT lewat JdbcTemplate.
-- Jadi tabelnya menuntut nilai yang hanya bisa diberikan jalur yang tidak dipakai.
--
-- Hasilnya: fitur undangan proyek tidak pernah bisa berhasil sejak baris itu ditulis.
-- Bukan kadang gagal — selalu.
--
-- Diberi default di sisi database, bukan ditambal di pernyataan INSERT-nya: kolom
-- NOT NULL yang tidak punya default dan tidak punya cara diisi adalah jebakan untuk
-- setiap penulis kueri berikutnya, bukan hanya untuk pernyataan yang satu itu.
-- Hibernate tetap mengirim nilainya sendiri lewat JPA, jadi default ini tidak
-- mengubah apa pun di jalur itu.

ALTER TABLE project_members ALTER COLUMN id         SET DEFAULT gen_random_uuid();
ALTER TABLE project_members ALTER COLUMN joined_at  SET DEFAULT now();
ALTER TABLE project_members ALTER COLUMN updated_at SET DEFAULT now();
