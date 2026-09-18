ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS estimated_hours NUMERIC(8,2);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'chk_tasks_estimated_hours_positive' AND conrelid = 'tasks'::regclass) THEN
        ALTER TABLE tasks ADD CONSTRAINT chk_tasks_estimated_hours_positive
            CHECK (estimated_hours IS NULL OR estimated_hours > 0) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'chk_tasks_date_order' AND conrelid = 'tasks'::regclass) THEN
        ALTER TABLE tasks ADD CONSTRAINT chk_tasks_date_order
            CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date) NOT VALID;
    END IF;
END $$;

ALTER TABLE tasks VALIDATE CONSTRAINT chk_tasks_estimated_hours_positive;
ALTER TABLE tasks VALIDATE CONSTRAINT chk_tasks_date_order;
