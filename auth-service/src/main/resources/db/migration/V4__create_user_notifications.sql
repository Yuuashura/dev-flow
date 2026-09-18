-- ============================================================
-- USER NOTIFICATIONS / INBOX
-- ============================================================

CREATE TABLE IF NOT EXISTS user_notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(50)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content      TEXT,
    metadata     JSONB        DEFAULT '{}'::jsonb,
    status       VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    priority     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    action_url   VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_status
    ON user_notifications(user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_type
    ON user_notifications(user_id, type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_expires_at
    ON user_notifications(expires_at) WHERE expires_at IS NOT NULL;
