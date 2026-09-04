ALTER TABLE jobs ADD COLUMN owner_subject VARCHAR(255);
ALTER TABLE jobs ADD COLUMN canonical_url VARCHAR(1000);
ALTER TABLE jobs ADD COLUMN source_portal VARCHAR(150);
ALTER TABLE jobs ADD COLUMN experience_text VARCHAR(500);

UPDATE jobs
SET canonical_url = LOWER(SPLIT_PART(job_url, '#', 1))
WHERE job_url IS NOT NULL;

CREATE INDEX idx_jobs_owner_subject ON jobs(owner_subject);
CREATE INDEX idx_jobs_canonical_url ON jobs(canonical_url);
CREATE UNIQUE INDEX uq_jobs_manual_canonical_url ON jobs(canonical_url) WHERE source = 'MANUAL';
