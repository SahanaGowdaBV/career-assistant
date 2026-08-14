-- Forward-only resume pipeline additions. Existing V2 rows are preserved.
ALTER TABLE resume_versions
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(500),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(150),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS version_number INTEGER,
    ADD COLUMN IF NOT EXISTS master_resume BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS parsed_text TEXT,
    ADD COLUMN IF NOT EXISTS structured_skills TEXT,
    ADD COLUMN IF NOT EXISTS structured_experience TEXT,
    ADD COLUMN IF NOT EXISTS source_resume_id UUID,
    ADD COLUMN IF NOT EXISTS customization_manifest TEXT;

UPDATE resume_versions
SET original_filename = COALESCE(original_filename, file_name, version_name),
    version_number = numbered.version_number
FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id)::INTEGER AS version_number
    FROM resume_versions
) AS numbered
WHERE resume_versions.id = numbered.id
  AND resume_versions.version_number IS NULL;

ALTER TABLE resume_versions
    ALTER COLUMN version_number SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'resume_versions'::regclass
          AND conname = 'fk_resume_versions_source_resume'
    ) THEN
        ALTER TABLE resume_versions
            ADD CONSTRAINT fk_resume_versions_source_resume
            FOREIGN KEY (source_resume_id)
            REFERENCES resume_versions(id)
            ON DELETE SET NULL
            NOT VALID;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_resume_versions_version_number
    ON resume_versions(version_number);

CREATE UNIQUE INDEX IF NOT EXISTS uq_resume_versions_active_master
    ON resume_versions(master_resume)
    WHERE master_resume = TRUE;

CREATE INDEX IF NOT EXISTS idx_resume_versions_created_at
    ON resume_versions(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_resume_versions_checksum
    ON resume_versions(checksum);

ALTER TABLE job_scores
    ADD COLUMN IF NOT EXISTS target_title_score NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS required_skills_score NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS preferred_skills_score NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS keyword_coverage_score NUMERIC(5, 2);
