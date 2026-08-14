-- ============================================
-- Career Assistant
-- V1 - Initial Schema
-- ============================================

-- ============================================
-- COMPANIES
-- ============================================

CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(255) NOT NULL,
    website VARCHAR(500),
    careers_url VARCHAR(500),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_companies_name UNIQUE (name)
);


-- ============================================
-- JOBS
-- ============================================

CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_id UUID NOT NULL,

    title VARCHAR(500) NOT NULL,
    description TEXT,

    location VARCHAR(500),
    country VARCHAR(100),
    city VARCHAR(150),

    employment_type VARCHAR(100),
    experience_min INTEGER,
    experience_max INTEGER,

    salary_min NUMERIC(12, 2),
    salary_max NUMERIC(12, 2),
    salary_currency VARCHAR(10),

    source VARCHAR(100) NOT NULL,
    source_job_id VARCHAR(500),
    job_url VARCHAR(1000) NOT NULL,

    posted_at TIMESTAMPTZ,
    scraped_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    status VARCHAR(50) NOT NULL DEFAULT 'NEW',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_jobs_company
        FOREIGN KEY (company_id)
        REFERENCES companies(id),

    CONSTRAINT uq_jobs_source_job
        UNIQUE (source, source_job_id)
);


-- ============================================
-- JOB SCORES
-- ============================================

CREATE TABLE job_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    job_id UUID NOT NULL,

    score NUMERIC(5, 2) NOT NULL,

    skills_score NUMERIC(5, 2),
    experience_score NUMERIC(5, 2),
    location_score NUMERIC(5, 2),
    salary_score NUMERIC(5, 2),

    matched_keywords TEXT,
    missing_keywords TEXT,

    scoring_reason TEXT,

    scored_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_job_scores_job
        FOREIGN KEY (job_id)
        REFERENCES jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_job_scores_job
        UNIQUE (job_id)
);


-- ============================================
-- APPLICATIONS
-- ============================================

CREATE TABLE applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    job_id UUID NOT NULL,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',

    application_method VARCHAR(50),

    applied_at TIMESTAMPTZ,

    failure_reason TEXT,

    application_url VARCHAR(1000),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_applications_job
        FOREIGN KEY (job_id)
        REFERENCES jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_applications_job
        UNIQUE (job_id)
);


-- ============================================
-- INDEXES
-- ============================================

CREATE INDEX idx_jobs_company_id
    ON jobs(company_id);

CREATE INDEX idx_jobs_status
    ON jobs(status);

CREATE INDEX idx_jobs_country
    ON jobs(country);

CREATE INDEX idx_jobs_source
    ON jobs(source);

CREATE INDEX idx_jobs_posted_at
    ON jobs(posted_at);

CREATE INDEX idx_job_scores_score
    ON job_scores(score);

CREATE INDEX idx_applications_status
    ON applications(status);