-- V12__create_media_assets.sql
--
-- Gambar milik user (foto profil, logo workspace) sebelumnya disimpan sebagai URL
-- eksternal. Itu menaruh tiga masalah sekaligus di dalam produk:
--
--   1. Server mengambil atau browser memuat host pilihan penyerang — SSRF dan
--      pelacakan pihak ketiga.
--   2. Isinya bisa berubah kapan saja setelah lolos pemeriksaan.
--   3. Tidak ada yang bisa memastikan yang di ujung sana benar-benar gambar.
--
-- Byte-nya sekarang disimpan di sini, setelah di-decode dan di-encode ulang, jadi
-- yang tersimpan dijamin gambar asli tanpa metadata atau muatan tempelan.

CREATE TABLE IF NOT EXISTS media_assets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Pemilik. Dipakai untuk kuota dan pembersihan; bukan kontrol akses baca,
    -- karena avatar memang dilihat anggota lain.
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    kind          VARCHAR(30) NOT NULL
                  CHECK (kind IN ('AVATAR', 'WORKSPACE_LOGO')),

    -- Ditentukan server dari byte hasil encode ulang, bukan dari header atau nama
    -- file yang dikirim klien.
    content_type  VARCHAR(50) NOT NULL
                  CHECK (content_type IN ('image/png', 'image/jpeg')),

    size_bytes    INTEGER NOT NULL CHECK (size_bytes > 0),
    width         INTEGER NOT NULL CHECK (width > 0),
    height        INTEGER NOT NULL CHECK (height > 0),

    -- SHA-256 dari byte tersimpan. Dipakai sebagai ETag, jadi gambar yang tidak
    -- berubah tidak dikirim ulang.
    sha256        VARCHAR(64) NOT NULL,

    data          BYTEA NOT NULL,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_media_assets_owner ON media_assets(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_media_assets_sha256 ON media_assets(sha256);
