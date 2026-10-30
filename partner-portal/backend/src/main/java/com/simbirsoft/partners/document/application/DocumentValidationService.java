package com.simbirsoft.partners.document.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("!auth-test")
public class DocumentValidationService {
    public static final int MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;
    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final MalwareScanner scanner;

    public DocumentValidationService(MalwareScanner scanner) { this.scanner = scanner; }

    /** Validate and scan before database or object storage writes. */
    public PreparedDocument prepare(String originalFileName, String suppliedContentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw badRequest();
        if (bytes.length > MAX_FILE_SIZE_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Document exceeds 20 MB");
        String fileName = safeFileName(originalFileName);
        String contentType = suppliedContentType == null ? ""
                : suppliedContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!((extension.equals("pdf") && PDF.equals(contentType))
                || (extension.equals("docx") && DOCX.equals(contentType)))) throw badRequest();
        // Snapshot once: validation, scanning and the checksum must describe the stored bytes.
        byte[] content = bytes.clone();
        if (extension.equals("pdf") && !startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII)))
            throw badRequest();
        if (extension.equals("docx") && !startsWith(content, new byte[] {'P', 'K', 3, 4})) throw badRequest();
        MalwareScanner.Result result;
        try { result = scanner.scan(content.clone()); }
        catch (RuntimeException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Document could not be checked");
        }
        if (result != MalwareScanner.Result.CLEAN)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Document did not pass security check");
        return new PreparedDocument(fileName, contentType, content, sha256(content));
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank() || value.length() > 512) throw badRequest();
        String fileName = value.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 255 || fileName.chars().anyMatch(c -> c < 32 || c == 127))
            throw badRequest();
        return fileName;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) if (value[index] != prefix[index]) return false;
        return true;
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document must be a PDF or DOCX file up to 20 MB");
    }
}
