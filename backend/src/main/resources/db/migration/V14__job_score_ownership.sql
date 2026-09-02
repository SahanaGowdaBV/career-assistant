ALTER TABLE job_scores ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255);
ALTER TABLE job_scores DROP CONSTRAINT IF EXISTS uq_job_scores_job;
DROP INDEX IF EXISTS uq_job_scores_job;
CREATE UNIQUE INDEX IF NOT EXISTS uq_job_scores_owned_job
    ON job_scores(job_id, owner_subject) WHERE owner_subject IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_job_scores_legacy_job
    ON job_scores(job_id) WHERE owner_subject IS NULL;
CREATE INDEX IF NOT EXISTS idx_job_scores_owner_subject ON job_scores(owner_subject);

ALTER TABLE applications DROP CONSTRAINT IF EXISTS uq_applications_job;
DROP INDEX IF EXISTS uq_applications_job;
DROP INDEX IF EXISTS uq_applications_idempotency_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_applications_owned_job
    ON applications(job_id, owner_subject) WHERE owner_subject IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_applications_legacy_job
    ON applications(job_id) WHERE owner_subject IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_applications_owned_idempotency
    ON applications(owner_subject, idempotency_key) WHERE owner_subject IS NOT NULL;

DROP INDEX IF EXISTS uq_resume_versions_active_master;
CREATE UNIQUE INDEX IF NOT EXISTS uq_resume_versions_owned_active_master
    ON resume_versions(owner_subject) WHERE master_resume = TRUE AND owner_subject IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_resume_versions_legacy_active_master
    ON resume_versions(master_resume) WHERE master_resume = TRUE AND owner_subject IS NULL;
