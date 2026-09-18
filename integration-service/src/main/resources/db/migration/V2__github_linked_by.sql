-- Track who linked the GitHub repo to each project. When reading commits from a
-- private repo, we need that person's token, not the viewer's token. The viewer
-- (a workspace CLIENT or other member) may not have GitHub connected at all.

ALTER TABLE projects ADD COLUMN IF NOT EXISTS github_linked_by UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_projects_github_linked_by ON projects(github_linked_by) WHERE github_linked_by IS NOT NULL;
