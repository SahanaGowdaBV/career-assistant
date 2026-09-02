ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255);
UPDATE user_settings SET owner_subject = 'legacy-profile' WHERE owner_subject IS NULL;
ALTER TABLE user_settings ALTER COLUMN owner_subject SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_settings_owner_subject ON user_settings(owner_subject);
