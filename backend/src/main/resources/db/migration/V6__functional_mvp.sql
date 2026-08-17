CREATE TABLE IF NOT EXISTS user_settings (id UUID PRIMARY KEY, profile_name VARCHAR(255), locations TEXT, roles TEXT, skills TEXT, experience_min INTEGER, experience_max INTEGER, dry_run BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
CREATE INDEX IF NOT EXISTS idx_jobs_city_status ON jobs(city,status);
CREATE INDEX IF NOT EXISTS idx_jobs_experience ON jobs(experience_min,experience_max);
CREATE INDEX IF NOT EXISTS idx_jobs_scraped_at ON jobs(scraped_at DESC);
