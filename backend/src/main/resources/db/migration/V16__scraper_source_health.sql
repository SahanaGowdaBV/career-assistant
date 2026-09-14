CREATE TABLE scraper_source_health (
    source_key VARCHAR(255) PRIMARY KEY,
    source_name VARCHAR(255) NOT NULL,
    source_kind VARCHAR(100) NOT NULL,
    source_status VARCHAR(50) NOT NULL,
    career_url VARCHAR(1000),
    discovered INTEGER NOT NULL DEFAULT 0,
    fetched INTEGER NOT NULL DEFAULT 0,
    accepted INTEGER NOT NULL DEFAULT 0,
    rejected INTEGER NOT NULL DEFAULT 0,
    duplicates INTEGER NOT NULL DEFAULT 0,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    error_type VARCHAR(255),
    last_run_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scraper_source_health_status ON scraper_source_health(source_status);
