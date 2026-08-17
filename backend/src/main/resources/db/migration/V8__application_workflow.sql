ALTER TABLE cover_letters
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(255);
UPDATE cover_letters SET content_type='application/vnd.openxmlformats-officedocument.wordprocessingml.document' WHERE content_type IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cover_letters_job ON cover_letters(job_id) WHERE job_id IS NOT NULL;

ALTER TABLE applications
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS adapter VARCHAR(30),
    ADD COLUMN IF NOT EXISTS confirmation_id VARCHAR(500),
    ADD COLUMN IF NOT EXISTS confirmation_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS notification_sent_at TIMESTAMPTZ;
UPDATE applications SET idempotency_key='job:' || job_id::text WHERE idempotency_key IS NULL;
ALTER TABLE applications ALTER COLUMN idempotency_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_applications_idempotency_key ON applications(idempotency_key);

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS current_location VARCHAR(100) DEFAULT 'India',
    ADD COLUMN IF NOT EXISTS relocation VARCHAR(100) DEFAULT 'UAE',
    ADD COLUMN IF NOT EXISTS notice_period_days INTEGER DEFAULT 90,
    ADD COLUMN IF NOT EXISTS notification_recipient VARCHAR(320) DEFAULT 'sahana.gowda2227@gmail.com',
    ADD COLUMN IF NOT EXISTS visa_answer TEXT,
    ADD COLUMN IF NOT EXISTS salary_answer TEXT,
    ADD COLUMN IF NOT EXISTS sponsorship_answer TEXT,
    ADD COLUMN IF NOT EXISTS legal_answers TEXT;

CREATE TABLE cleanup_audit_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), run_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    dry_run BOOLEAN NOT NULL, category VARCHAR(80) NOT NULL, entity_id UUID,
    storage_path VARCHAR(1000), action VARCHAR(30) NOT NULL, details TEXT
);
