-- Flyway baseline-on-migrate di database yang sudah berisi tabel service lain
-- menulis baris baseline di versi 1, lalu skip V1__init. Tabel processed_events
-- tidak pernah dibuat. Drop history notification-service supaya Flyway jalankan
-- V1 dari awal.

DROP TABLE IF EXISTS flyway_schema_history_notification;
