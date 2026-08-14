package career.assistant.application;

import career.assistant.application.entity.Application;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationSchemaContractTest {

    @Test
    void entityAndForwardMigrationUseCanonicalResumeVersionColumn() throws Exception {
        Column column = Application.class
                .getDeclaredField("resumeVersionId")
                .getAnnotation(Column.class);

        assertNotNull(column);
        assertEquals("resume_version_id", column.name());

        String migration = migrationSql();
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS resume_version_id UUID"));
        assertTrue(migration.contains("SET resume_version_id = resume_id"));
        assertTrue(migration.contains("DROP COLUMN resume_id"));
        assertTrue(migration.contains("fk_applications_resume_version"));
        assertTrue(migration.contains("idx_applications_resume_version_id"));
    }

    @Test
    void migrationAddsEveryEntityColumnAndRepositoryIndex() throws IOException {
        String migration = migrationSql();

        for (String column : new String[]{
                "application_type", "application_url", "cover_letter_id", "applied_at",
                "reviewed_at", "error_message", "notes", "created_at", "updated_at"
        }) {
            assertTrue(migration.contains(column), "Missing migration coverage for " + column);
        }
        assertTrue(migration.contains("uq_applications_job"));
        assertTrue(migration.contains("ALTER COLUMN job_id SET NOT NULL"));
        assertTrue(migration.contains("idx_applications_job_id"));
        assertTrue(migration.contains("idx_applications_status"));
        assertFalse(migration.contains("DROP TABLE"));
    }

    private String migrationSql() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V5__reconcile_applications_schema.sql"
        )) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
