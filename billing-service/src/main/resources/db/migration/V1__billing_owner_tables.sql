-- V1__billing_owner_tables.sql
--
-- Dua tabel ini milik billing-service dan sampai sekarang tidak pernah punya migrasi
-- sama sekali: keduanya hanya ada karena `ddl-auto: update` membangunnya dari entity.
-- Artinya lingkungan baru yang menjalankan riwayat migrasi dari awal tidak akan punya
-- tabel ini, dan tidak ada catatan tertulis tentang bentuknya selain kelas Java.
--
-- Bentuk di bawah diambil dari database yang sedang berjalan, jadi menjalankannya di
-- sana adalah no-op. Yang berubah adalah: skema ini sekarang tercatat, dan
-- `ddl-auto: validate` bisa dipasang tanpa memutus lingkungan baru.
--
-- Indeks dan foreign key yang tidak pernah dibuat ddl-auto ikut ditambahkan.

CREATE TABLE IF NOT EXISTS owner_plans (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL UNIQUE,
    plan_code   VARCHAR(30) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    plan_start  TIMESTAMPTZ,
    plan_end    TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS owner_payment_transactions (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL,

    -- external_id adalah kunci idempotency callback Xendit. UNIQUE-nya yang mencegah
    -- satu pembayaran diproses dua kali.
    external_id             VARCHAR(255) NOT NULL UNIQUE,

    -- BIGINT, bukan NUMERIC: Xendit mengirim rupiah dalam satuan bulat, dan menyimpan
    -- uang sebagai bilangan bulat menghindari kesalahan pembulatan sepenuhnya.
    amount                  BIGINT NOT NULL,
    currency                VARCHAR(10) NOT NULL,

    status                  VARCHAR(30) NOT NULL,
    provider                VARCHAR(50),
    provider_transaction_id VARCHAR(255),
    payment_method          VARCHAR(255),
    payment_channel         VARCHAR(255),
    description             VARCHAR(255),
    invoice_url             VARCHAR(1000),
    paid_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tidak pernah ada: jalur rekonsiliasi memindai transaksi PENDING setiap 60 detik,
-- dan halaman billing memuat riwayat per pengguna. Keduanya sequential scan tanpa ini.
CREATE INDEX IF NOT EXISTS idx_owner_payment_tx_user ON owner_payment_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_owner_payment_tx_status ON owner_payment_transactions(status, created_at);
CREATE INDEX IF NOT EXISTS idx_owner_plans_active ON owner_plans(active) WHERE active = true;

-- Foreign key ke users tidak pernah dibuat: billing-service tidak punya entity User,
-- jadi ddl-auto tidak tahu relasinya ada. Tanpa ini, menghapus pengguna meninggalkan
-- baris paket dan transaksi yang menunjuk ke tidak ada apa-apa.
--
-- NOT VALID: baris lama tidak diperiksa, jadi data yang terlanjur yatim tidak
-- menggagalkan migrasi. Penulisan baru tetap diperiksa penuh.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_owner_plans_user') THEN
            ALTER TABLE owner_plans
                ADD CONSTRAINT fk_owner_plans_user
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE NOT VALID;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_owner_payment_tx_user') THEN
            ALTER TABLE owner_payment_transactions
                ADD CONSTRAINT fk_owner_payment_tx_user
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE NOT VALID;
        END IF;
    END IF;
END $$;
