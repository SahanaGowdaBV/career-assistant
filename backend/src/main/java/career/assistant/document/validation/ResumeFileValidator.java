package career.assistant.document.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ResumeFileValidator {

    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Set<String> ACTIVE_PDF_MARKERS = Set.of(
            "/javascript", "/js", "/launch", "/embeddedfile", "/richmedia", "/openaction", "/aa"
    );

    private final long maximumSize;

    public ResumeFileValidator(@Value("${career.resume.max-size-bytes:5242880}") long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public ValidatedResumeFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResumeValidationException("A PDF or DOCX resume file is required");
        }
        if (file.getSize() > maximumSize) {
            throw new ResumeValidationException("Resume exceeds the maximum allowed size of " + maximumSize + " bytes");
        }

        String declaredType = normalizeContentType(file.getContentType());
        if (!PDF.equals(declaredType) && !DOCX.equals(declaredType)) {
            throw new ResumeValidationException("Only PDF and DOCX resumes are accepted");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResumeValidationException("Resume file could not be read", exception);
        }
        if (content.length == 0 || content.length > maximumSize) {
            throw new ResumeValidationException("Resume file size is invalid");
        }

        String extension;
        if (isPdf(content)) {
            extension = "pdf";
            if (!PDF.equals(declaredType)) {
                throw new ResumeValidationException("Resume MIME type does not match its PDF signature");
            }
            rejectActivePdfContent(content);
        } else if (isZip(content)) {
            extension = "docx";
            if (!DOCX.equals(declaredType)) {
                throw new ResumeValidationException("Resume MIME type does not match its DOCX signature");
            }
            validateDocxArchive(content);
        } else {
            throw new ResumeValidationException("Resume file signature is not a valid PDF or DOCX");
        }

        String sanitized = sanitizeFilename(file.getOriginalFilename(), extension);
        return new ValidatedResumeFile(sanitized, declaredType, extension, content, sha256(content));
    }

    public String sanitizeFilename(String originalFilename, String requiredExtension) {
        String input = originalFilename == null ? "resume" : Normalizer.normalize(originalFilename, Normalizer.Form.NFKC);
        input = input.replace('\\', '/');
        input = input.substring(input.lastIndexOf('/') + 1);
        input = input.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[^A-Za-z0-9._ -]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("_+", "_")
                .trim();
        input = input.replaceFirst("^[. ]+", "");
        input = input.replaceFirst("(?i)\\.(pdf|docx)$", "");
        input = input.replaceAll("[. ]+$", "");
        if (input.isBlank()) {
            input = "resume";
        }
        if (input.length() > 120) {
            input = input.substring(0, 120).replaceAll("[. ]+$", "");
        }
        return input + "." + requiredExtension;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean isPdf(byte[] content) {
        return content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-';
    }

    private boolean isZip(byte[] content) {
        return content.length >= 4 && content[0] == 'P' && content[1] == 'K'
                && ((content[2] == 3 && content[3] == 4) || (content[2] == 5 && content[3] == 6));
    }

    private void rejectActivePdfContent(byte[] content) {
        String raw = new String(content, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        if (ACTIVE_PDF_MARKERS.stream().anyMatch(raw::contains)) {
            throw new ResumeValidationException("PDF contains active or embedded executable content");
        }
    }

    private void validateDocxArchive(byte[] content) {
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        int entries = 0;
        long expandedBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > 2_000) {
                    throw new ResumeValidationException("DOCX archive contains too many entries");
                }
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (name.startsWith("/") || name.contains("../")) {
                    throw new ResumeValidationException("DOCX archive contains an unsafe path");
                }
                if (name.equals("[content_types].xml")) {
                    hasContentTypes = true;
                } else if (name.equals("word/document.xml")) {
                    hasDocument = true;
                }
                if (name.contains("vbaproject") || name.startsWith("word/embeddings/")
                        || name.startsWith("word/activex/") || name.endsWith(".bin")
                        || name.endsWith(".exe") || name.endsWith(".com") || name.endsWith(".js")) {
                    throw new ResumeValidationException("DOCX contains executable or embedded content");
                }
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > 50L * 1024 * 1024) {
                        throw new ResumeValidationException("DOCX expanded content is too large");
                    }
                }
            }
        } catch (IOException exception) {
            throw new ResumeValidationException("DOCX archive is invalid", exception);
        }
        if (!hasContentTypes || !hasDocument) {
            throw new ResumeValidationException("File signature is ZIP but not a valid DOCX document");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
