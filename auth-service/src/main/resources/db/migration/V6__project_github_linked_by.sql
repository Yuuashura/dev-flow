-- Catat siapa yang menghubungkan repo GitHub ke tiap proyek.
--
-- Commit dibaca dengan token pemilik koneksi, bukan token penonton. Sebelum ini
-- listProjectCommits memakai token user yang sedang login: seorang CLIENT tidak
-- punya baris di github_connections, jadi tokennya null, dan GitHub membalas 404
-- untuk repo private (GitHub menyamarkan 403 jadi 404 agar keberadaan repo tidak
-- bocor). Akibatnya riwayat commit selalu kosong bagi klien.
--
-- ON DELETE SET NULL: kalau akun yang menghubungkan dihapus, link repo tetap ada
-- tapi kehilangan token — dan listProjectCommits akan memberi pesan "hubungkan
-- ulang" alih-alih gagal diam-diam.

ALTER TABLE projects ADD COLUMN IF NOT EXISTS github_linked_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- Isi untuk proyek yang sudah terhubung: pakai pembuat proyek sebagai tebakan
-- terbaik, hanya bila dia memang punya koneksi GitHub.
UPDATE projects p
SET github_linked_by = p.created_by
WHERE p.github_repo_owner IS NOT NULL
  AND p.github_linked_by IS NULL
  AND EXISTS (SELECT 1 FROM github_connections gc WHERE gc.user_id = p.created_by);
