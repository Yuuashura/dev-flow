-- V10__backfill_project_progress.sql
--
-- progress_percent hanya pernah ditulis oleh endpoint updateProjectMeta, dan tidak
-- ada satu pun kontrol di UI yang mengubahnya — jadi angkanya tidak pernah bergerak
-- dari 0 seberapa pun banyak Flow yang diselesaikan. Mulai sekarang ProjectService
-- menghitungnya ulang di setiap perubahan Flow; baris yang sudah ada perlu dikejar
-- sekali di sini, kalau tidak proyek lama tetap 0% sampai ada Flow yang disentuh.

UPDATE projects p
SET progress_percent = sub.percent
FROM (
    SELECT t.project_id,
           CASE
               WHEN COUNT(*) = 0 THEN 0
               WHEN COUNT(*) FILTER (WHERE t.status = 'DONE') = COUNT(*) THEN 100
               ELSE (COUNT(*) FILTER (WHERE t.status = 'DONE') * 100 / COUNT(*))::int
           END AS percent
    FROM tasks t
    GROUP BY t.project_id
) AS sub
WHERE p.id = sub.project_id
  AND p.progress_percent IS DISTINCT FROM sub.percent;

-- Status ikut jadi konsisten dengan progres yang baru dihitung. ON_HOLD dan
-- CANCELLED dipilih manusia secara eksplisit, jadi tidak disentuh.
UPDATE projects
SET status = 'COMPLETED'
WHERE progress_percent >= 100
  AND status IN ('PLANNING', 'IN_PROGRESS');

UPDATE projects
SET status = 'IN_PROGRESS'
WHERE progress_percent > 0
  AND progress_percent < 100
  AND status = 'PLANNING';
