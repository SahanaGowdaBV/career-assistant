ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS legal_name VARCHAR(255);
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS application_email VARCHAR(320);
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS application_phone VARCHAR(100);
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS linkedin_url VARCHAR(1000);
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS consent_answers TEXT;
