ALTER TABLE cover_letters DROP CONSTRAINT IF EXISTS uq_cover_letters_job;
DROP INDEX IF EXISTS uq_cover_letters_job;
CREATE INDEX IF NOT EXISTS ix_cover_letters_job_created ON cover_letters(job_id, created_at DESC);

ALTER TABLE applications ADD COLUMN IF NOT EXISTS submission_attempted_at TIMESTAMPTZ;
