CREATE TABLE IF NOT EXISTS applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    job_id UUID NOT NULL,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',

    application_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',

    application_url VARCHAR(1000),

    resume_id UUID,

    cover_letter_id UUID,

    applied_at TIMESTAMPTZ,

    reviewed_at TIMESTAMPTZ,

    error_message TEXT,

    notes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_applications_job
        FOREIGN KEY (job_id)
        REFERENCES jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_applications_job
        UNIQUE (job_id)
);

CREATE INDEX IF NOT EXISTS idx_applications_status
    ON applications(status);

CREATE INDEX IF NOT EXISTS idx_applications_created_at
    ON applications(created_at);

CREATE INDEX IF NOT EXISTS idx_applications_job_id
    ON applications(job_id);
