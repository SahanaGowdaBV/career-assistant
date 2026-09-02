ALTER TABLE applications ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255);
ALTER TABLE resume_versions ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255);
ALTER TABLE cover_letters ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_applications_owner_subject ON applications(owner_subject);
CREATE INDEX IF NOT EXISTS idx_resume_versions_owner_subject ON resume_versions(owner_subject);
CREATE INDEX IF NOT EXISTS idx_cover_letters_owner_subject ON cover_letters(owner_subject);
