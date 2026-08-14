-- ============================================
-- Career Assistant
-- V2 - Resume Versions & Cover Letters
-- ============================================

-- ============================================
-- RESUME VERSIONS
-- ============================================

CREATE TABLE resume_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    job_id UUID,
    application_id UUID,

    version_name VARCHAR(255) NOT NULL,

    file_name VARCHAR(500),
    file_path VARCHAR(1000),
    storage_path VARCHAR(1000),

    original_resume BOOLEAN NOT NULL DEFAULT FALSE,
    customized BOOLEAN NOT NULL DEFAULT FALSE,

    keywords_added TEXT,
    keywords_removed TEXT,
    customization_summary TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_resume_versions_job
        FOREIGN KEY (job_id)
        REFERENCES jobs(id)
        ON DELETE SET NULL
);


-- ============================================
-- COVER LETTERS
-- ============================================

CREATE TABLE cover_letters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    job_id UUID,
    application_id UUID,

    title VARCHAR(255) NOT NULL,

    content TEXT NOT NULL,

    file_name VARCHAR(500),
    file_path VARCHAR(1000),
    storage_path VARCHAR(1000),

    customized BOOLEAN NOT NULL DEFAULT FALSE,

    keywords_used TEXT,
    customization_summary TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_cover_letters_job
        FOREIGN KEY (job_id)
        REFERENCES jobs(id)
        ON DELETE SET NULL
);


-- ============================================
-- ADD CUSTOMIZED DOCUMENT REFERENCES
-- TO APPLICATIONS
-- ============================================

ALTER TABLE applications
ADD COLUMN resume_version_id UUID;

ALTER TABLE applications
ADD COLUMN cover_letter_id UUID;


-- ============================================
-- APPLICATION DOCUMENT FOREIGN KEYS
-- ============================================

ALTER TABLE applications
ADD CONSTRAINT fk_applications_resume_version
    FOREIGN KEY (resume_version_id)
    REFERENCES resume_versions(id)
    ON DELETE SET NULL;

ALTER TABLE applications
ADD CONSTRAINT fk_applications_cover_letter
    FOREIGN KEY (cover_letter_id)
    REFERENCES cover_letters(id)
    ON DELETE SET NULL;


-- ============================================
-- INDEXES
-- ============================================

CREATE INDEX idx_resume_versions_job_id
    ON resume_versions(job_id);

CREATE INDEX idx_resume_versions_application_id
    ON resume_versions(application_id);

CREATE INDEX idx_cover_letters_job_id
    ON cover_letters(job_id);

CREATE INDEX idx_cover_letters_application_id
    ON cover_letters(application_id);

CREATE INDEX idx_applications_resume_version_id
    ON applications(resume_version_id);

CREATE INDEX idx_applications_cover_letter_id
    ON applications(cover_letter_id);