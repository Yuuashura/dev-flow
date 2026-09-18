-- ============================================================
-- 1. WORKSPACE SERVICE TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS workspaces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id   UUID         NOT NULL REFERENCES users(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    logo_url        TEXT,
    business_type   VARCHAR(50),
    timezone        VARCHAR(50),
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_workspaces_owner ON workspaces (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_workspaces_slug ON workspaces (slug);

CREATE TABLE IF NOT EXISTS workspace_members (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces(id),
    user_id       UUID        NOT NULL REFERENCES users(id),
    role          VARCHAR(30) NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_workspace_user UNIQUE (workspace_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_wm_user ON workspace_members (user_id);
CREATE INDEX IF NOT EXISTS idx_wm_workspace ON workspace_members (workspace_id);

CREATE TABLE IF NOT EXISTS invitations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id),
    email         VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    token         VARCHAR(255) NOT NULL UNIQUE,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    invited_by    UUID         NOT NULL REFERENCES users(id),
    expires_at    TIMESTAMPTZ  NOT NULL,
    accepted_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS workspace_settings (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id),
    key           VARCHAR(100) NOT NULL,
    value         TEXT,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_workspace_key UNIQUE (workspace_id, key)
);

-- ============================================================
-- 2. PROJECT SERVICE TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS projects (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     UUID         NOT NULL REFERENCES workspaces(id),
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PLANNING',
    client_visible   BOOLEAN      NOT NULL DEFAULT true,
    start_date       DATE,
    target_date      DATE,
    progress_percent INT          NOT NULL DEFAULT 0,
    created_by       UUID         NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_projects_workspace ON projects (workspace_id);

CREATE TABLE IF NOT EXISTS tasks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID         NOT NULL REFERENCES projects(id),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    status       VARCHAR(30)  NOT NULL DEFAULT 'TODO',
    priority     VARCHAR(30)  NOT NULL DEFAULT 'MEDIUM',
    assigned_to  UUID         REFERENCES users(id),
    due_date     DATE,
    completed_at TIMESTAMPTZ,
    position     INT          NOT NULL DEFAULT 0,
    created_by   UUID         NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks (project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON tasks (assigned_to);

CREATE TABLE IF NOT EXISTS milestones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID         NOT NULL REFERENCES projects(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    due_date    DATE,
    position    INT          NOT NULL DEFAULT 0,
    created_by  UUID         NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS revisions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id UUID         NOT NULL REFERENCES milestones(id),
    project_id   UUID         NOT NULL REFERENCES projects(id),
    title        VARCHAR(255) NOT NULL,
    description  TEXT         NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'SUBMITTED',
    submitted_by UUID         NOT NULL REFERENCES users(id),
    assigned_to  UUID         REFERENCES users(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- 3. BILLING SERVICE TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS plans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    description      TEXT,
    price_amount     BIGINT       NOT NULL DEFAULT 0,
    currency         VARCHAR(10)  NOT NULL DEFAULT 'IDR',
    billing_interval VARCHAR(30)  NOT NULL DEFAULT 'MONTHLY',
    trial_days       INT          NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id         UUID        NOT NULL UNIQUE REFERENCES workspaces(id),
    plan_id              UUID        NOT NULL REFERENCES plans(id),
    status               VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    current_period_start TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end   TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '30 days'),
    cancelled_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id         UUID        NOT NULL REFERENCES subscriptions(id),
    workspace_id            UUID        NOT NULL REFERENCES workspaces(id),
    amount                  BIGINT      NOT NULL,
    currency                VARCHAR(10) NOT NULL DEFAULT 'IDR',
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    provider                VARCHAR(50) DEFAULT 'XENDIT',
    provider_transaction_id VARCHAR(255),
    payment_method          VARCHAR(50),
    paid_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 4. INTEGRATION SERVICE TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS github_installations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id),
    installation_id VARCHAR(255) NOT NULL UNIQUE,
    account_login   VARCHAR(255) NOT NULL,
    account_type    VARCHAR(30)  NOT NULL DEFAULT 'USER',
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    installed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS normalized_github_activities (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID        NOT NULL REFERENCES projects(id),
    workspace_id  UUID        NOT NULL REFERENCES workspaces(id),
    activity_type VARCHAR(50) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    author        VARCHAR(255) NOT NULL,
    url           TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
