-- Catatan event yang sudah diproses, dipakai untuk idempotensi consumer.
-- Redelivery RabbitMQ (retry / restart) tidak boleh mengirim email dua kali.
CREATE TABLE IF NOT EXISTS processed_events (
    id            UUID         PRIMARY KEY,
    event_id      VARCHAR(255) NOT NULL UNIQUE,
    consumer_name VARCHAR(100) NOT NULL,
    event_type    VARCHAR(100),
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_event_id ON processed_events (event_id);
