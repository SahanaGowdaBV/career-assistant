CREATE TABLE submission_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES applications(id),
    owner_subject VARCHAR(255) NOT NULL,
    ats_provider VARCHAR(40) NOT NULL,
    source_fingerprint VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    state VARCHAR(30) NOT NULL,
    confirmation_reference VARCHAR(500),
    confirmation_url VARCHAR(1000),
    failure_category VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_submission_attempt_state CHECK (state IN ('DRY_RUN','IN_PROGRESS','CONFIRMED','REVIEW_REQUIRED','UNCERTAIN','FAILED'))
);
CREATE UNIQUE INDEX uq_submission_attempt_idempotency ON submission_attempts(idempotency_key);
CREATE UNIQUE INDEX uq_submission_attempt_active_application ON submission_attempts(application_id) WHERE state IN ('IN_PROGRESS','CONFIRMED');
CREATE UNIQUE INDEX uq_submission_attempt_active_source ON submission_attempts(source_fingerprint) WHERE state IN ('IN_PROGRESS','CONFIRMED');
CREATE INDEX ix_submission_attempt_owner_created ON submission_attempts(owner_subject, created_at);
