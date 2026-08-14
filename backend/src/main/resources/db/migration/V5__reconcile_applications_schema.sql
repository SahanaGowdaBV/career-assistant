-- Reconcile the effective V1-V4 schema with the Application JPA entity.
-- This migration is intentionally forward-only and safe to run when V1-V4 have
-- already been applied. Legacy columns not conflicting with the entity remain.

ALTER TABLE applications
    ADD COLUMN IF NOT EXISTS application_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS application_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS resume_version_id UUID,
    ADD COLUMN IF NOT EXISTS cover_letter_id UUID,
    ADD COLUMN IF NOT EXISTS applied_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

-- V3 introduced resume_id, while V2 and the resume_versions table use
-- resume_version_id. Preserve any V3 data before removing the ambiguous column.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'applications'
          AND column_name = 'resume_id'
    ) THEN
        EXECUTE '
            UPDATE applications
            SET resume_version_id = resume_id
            WHERE resume_version_id IS NULL
              AND resume_id IS NOT NULL';
        EXECUTE 'ALTER TABLE applications DROP COLUMN resume_id';
    END IF;
END
$$;

-- Preserve failure details from the V1 name when the entity-facing field is empty.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'applications'
          AND column_name = 'failure_reason'
    ) THEN
        EXECUTE '
            UPDATE applications
            SET error_message = failure_reason
            WHERE error_message IS NULL
              AND failure_reason IS NOT NULL';
    END IF;
END
$$;

UPDATE applications
SET application_type = 'MANUAL'
WHERE application_type IS NULL;

UPDATE applications
SET status = 'PENDING_REVIEW'
WHERE status IS NULL;

UPDATE applications
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

UPDATE applications
SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE updated_at IS NULL;

ALTER TABLE applications
    ALTER COLUMN job_id SET NOT NULL,
    ALTER COLUMN application_type SET DEFAULT 'MANUAL',
    ALTER COLUMN application_type SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'PENDING_REVIEW',
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET NOT NULL;

-- Add constraints only when an equivalent named constraint is not already present.
-- NOT VALID avoids rejecting unrelated historical rows while still enforcing all
-- inserts and updates performed after this migration.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'applications'::regclass
          AND conname = 'fk_applications_job'
    ) THEN
        ALTER TABLE applications
            ADD CONSTRAINT fk_applications_job
            FOREIGN KEY (job_id) REFERENCES jobs(id)
            ON DELETE CASCADE NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'applications'::regclass
          AND conname = 'uq_applications_job'
    ) THEN
        ALTER TABLE applications
            ADD CONSTRAINT uq_applications_job UNIQUE (job_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'applications'::regclass
          AND conname = 'fk_applications_resume_version'
    ) THEN
        ALTER TABLE applications
            ADD CONSTRAINT fk_applications_resume_version
            FOREIGN KEY (resume_version_id) REFERENCES resume_versions(id)
            ON DELETE SET NULL NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'applications'::regclass
          AND conname = 'fk_applications_cover_letter'
    ) THEN
        ALTER TABLE applications
            ADD CONSTRAINT fk_applications_cover_letter
            FOREIGN KEY (cover_letter_id) REFERENCES cover_letters(id)
            ON DELETE SET NULL NOT VALID;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_applications_job_id
    ON applications(job_id);

CREATE INDEX IF NOT EXISTS idx_applications_status
    ON applications(status);

CREATE INDEX IF NOT EXISTS idx_applications_created_at
    ON applications(created_at);

CREATE INDEX IF NOT EXISTS idx_applications_resume_version_id
    ON applications(resume_version_id);

CREATE INDEX IF NOT EXISTS idx_applications_cover_letter_id
    ON applications(cover_letter_id);
