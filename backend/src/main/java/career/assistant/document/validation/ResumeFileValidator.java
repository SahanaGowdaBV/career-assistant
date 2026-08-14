package career.assistant.document.validation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ResumeFileValidator {

    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final int MAXIMUM_PDF_OBJECTS = 100_000;
    private static final COSName JAVASCRIPT = COSName.getPDFName("JavaScript");
    private static final COSName LAUNCH = COSName.getPDFName("Launch");
    private static final COSName URI_ACTION = COSName.getPDFName("URI");
    private static final COSName EMBEDDED_FILE = COSName.getPDFName("EmbeddedFile");
    private static final COSName EMBEDDED_FILES = COSName.getPDFName("EmbeddedFiles");
    private static final COSName FILE_ATTACHMENT = COSName.getPDFName("FileAttachment");
    private static final COSName RICH_MEDIA = COSName.getPDFName("RichMedia");
    private static final COSName ACTION = COSName.getPDFName("Action");
    private static final COSName OPEN_ACTION = COSName.getPDFName("OpenAction");
    private static final COSName NEXT = COSName.getPDFName("Next");
    private static final Set<String> SAFE_URI_SCHEMES = Set.of("https", "http", "mailto");

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
        try (PDDocument document = Loader.loadPDF(content)) {
            inspectPdfObjectGraph(document.getDocument().getTrailer());
        } catch (ResumeValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResumeValidationException("PDF document is invalid", exception);
        }
    }

    private void inspectPdfObjectGraph(COSBase root) {
        ArrayDeque<PdfObject> pending = new ArrayDeque<>();
        Map<COSBase, Boolean> visited = new IdentityHashMap<>();
        Map<COSBase, Boolean> inspectedActions = new IdentityHashMap<>();
        pending.add(new PdfObject(root, false));

        while (!pending.isEmpty()) {
            PdfObject candidate = pending.removeLast();
            COSBase current = candidate.value();
            if (current == null) {
                continue;
            }

            if (current instanceof COSObject object) {
                addIfPresent(pending, object.getObject(), candidate.action());
            } else if (current instanceof COSDictionary dictionary) {
                boolean action = candidate.action() || ACTION.equals(dictionary.getCOSName(COSName.TYPE));
                if (action && inspectedActions.put(dictionary, Boolean.TRUE) == null) {
                    inspectPdfAction(dictionary);
                }
                if (visited.put(dictionary, Boolean.TRUE) == null) {
                    inspectPdfDictionary(dictionary, pending);
                    dictionary.forEach((key, value) -> addIfPresent(
                            pending, value, COSName.A.equals(key) || OPEN_ACTION.equals(key) || NEXT.equals(key)
                    ));
                }
            } else if (current instanceof COSArray array) {
                if (visited.put(array, Boolean.TRUE) == null) {
                    array.forEach(value -> addIfPresent(pending, value, candidate.action()));
                }
            } else {
                visited.put(current, Boolean.TRUE);
            }

            if (visited.size() + inspectedActions.size() > MAXIMUM_PDF_OBJECTS) {
                rejectActivePdfContent();
            }
        }
    }

    private void inspectPdfDictionary(COSDictionary dictionary, ArrayDeque<PdfObject> pending) {
        COSName objectType = dictionary.getCOSName(COSName.TYPE);
        COSName annotationSubtype = dictionary.getCOSName(COSName.SUBTYPE);

        if (EMBEDDED_FILE.equals(objectType)
                || FILE_ATTACHMENT.equals(annotationSubtype)
                || RICH_MEDIA.equals(annotationSubtype)
                || dictionary.getDictionaryObject(COSName.AF) != null) {
            rejectActivePdfContent();
        }

        if (COSName.CATALOG.equals(objectType)) {
            COSDictionary names = resolveDictionary(dictionary.getDictionaryObject(COSName.NAMES));
            if (names != null && (names.getDictionaryObject(JAVASCRIPT) != null
                    || names.getDictionaryObject(EMBEDDED_FILES) != null)) {
                rejectActivePdfContent();
            }
        }

        COSDictionary additionalActions = resolveDictionary(dictionary.getDictionaryObject(COSName.AA));
        if (additionalActions != null) {
            additionalActions.getValues().forEach(value -> addIfPresent(pending, value, true));
        }
    }

    private void inspectPdfAction(COSDictionary dictionary) {
        COSName actionType = dictionary.getCOSName(COSName.S);
        if (JAVASCRIPT.equals(actionType) || LAUNCH.equals(actionType)) {
            rejectActivePdfContent();
        }
        if (URI_ACTION.equals(actionType) && !isSafeUri(dictionary.getString(COSName.URI))) {
            rejectActivePdfContent();
        }
    }

    private COSDictionary resolveDictionary(COSBase value) {
        while (value instanceof COSObject object) {
            value = object.getObject();
        }
        return value instanceof COSDictionary dictionary ? dictionary : null;
    }

    private boolean isSafeUri(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!SAFE_URI_SCHEMES.contains(scheme)) {
                return false;
            }
            return "mailto".equals(scheme) ? !uri.getSchemeSpecificPart().isBlank() : uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private void addIfPresent(ArrayDeque<PdfObject> pending, COSBase value, boolean action) {
        if (value != null) {
            pending.add(new PdfObject(value, action));
        }
    }

    private void rejectActivePdfContent() {
        throw new ResumeValidationException("PDF contains active or embedded executable content");
    }

    private record PdfObject(COSBase value, boolean action) {
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
