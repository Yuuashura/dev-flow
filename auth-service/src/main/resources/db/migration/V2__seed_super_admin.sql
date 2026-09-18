-- Seed Super Admin — DINONAKTIFKAN.
--
-- Berkas ini dulu menyisipkan akun SUPER_ADMIN dengan alamat tetap
-- (admin@devflow.id) dan password yang diketahui publik: 'Admin123!', hash BCrypt-nya
-- ikut ter-commit di repo ini. Siapa pun yang pernah membaca repo bisa masuk sebagai
-- admin platform di lingkungan mana pun yang menjalankan migrasi ini.
--
-- Migrasi ini tidak pernah benar-benar berjalan — Flyway tidak punya
-- autoconfiguration di service ini sampai spring-boot-flyway ditambahkan — jadi
-- backdoor-nya tidak pernah terbentuk. Isinya dikosongkan sekarang supaya tetap
-- begitu, termasuk pada lingkungan baru yang menjalankan seluruh riwayat dari awal.
--
-- Untuk memberi akses admin, naikkan akun yang sudah ada dan dikenal:
--
--   UPDATE users SET global_role = 'SUPER_ADMIN' WHERE email = '<email anda>';
--
-- Cara itu tidak meninggalkan kredensial di source control.

SELECT 1;
