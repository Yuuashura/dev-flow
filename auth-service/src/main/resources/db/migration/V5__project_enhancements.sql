-- ============================================================
-- PROJECT ENHANCEMENTS: GitHub repo, demo, manual progress, comments
-- ============================================================

-- Projects: demo & linked GitHub repository columns
ALTER TABLE projects ADD COLUMN IF NOT EXISTS demo_url            VARCHAR(1000);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS github_repo_owner   VARCHAR(255);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS github_repo_name    VARCHAR(255);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS github_repo_branch  VARCHAR(255);

-- GitHub connections (Vercel-style personal OAuth token per user)
CREATE TABLE IF NOT EXISTS github_connections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_login    VARCHAR(255) NOT NULL,
    access_token    TEXT NOT NULL,
    scope           VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_github_connections_user UNIQUE (user_id)
);

-- Comments: polymorphic threads on PROJECT or REVISION
CREATE TABLE IF NOT EXISTS comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL,          -- 'PROJECT' | 'REVISION'
    entity_id   UUID NOT NULL,
    author_id   UUID NOT NULL REFERENCES users(id),
    content     TEXT NOT NULL,
    is_internal BOOLEAN NOT NULL DEFAULT false, -- jika true, hanya tim (OWNER/DEVELOPER) yang bisa lihat
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_comments_project_entity
    ON comments(project_id, entity_type, entity_id, created_at DESC);