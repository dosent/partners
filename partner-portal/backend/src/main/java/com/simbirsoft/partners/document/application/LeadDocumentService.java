package com.simbirsoft.partners.document.application;

import com.simbirsoft.partners.document.domain.LeadAttachment;
import com.simbirsoft.partners.document.domain.LeadAttachmentStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("!auth-test")
public class LeadDocumentService {
    public static final int MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;
    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final LeadAttachmentStore attachments;
    private final ObjectStorage storage;
    private final MalwareScanner scanner;

    public LeadDocumentService(LeadAttachmentStore attachments, ObjectStorage storage, MalwareScanner scanner) {
        this.attachments = attachments;
        this.storage = storage;
        this.scanner = scanner;
    }

    /** Validate and scan before any lead, metadata or object storage write is performed. */
    public PreparedAttachment prepare(String originalFileName, String suppliedContentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_SIZE_BYTES)
            throw badRequest();
        String fileName = safeFileName(originalFileName);
        String contentType = normalizeContentType(suppliedContentType);
        String extension = extension(fileName);
        if (!((extension.equals("pdf") && PDF.equals(contentType))
                || (extension.equals("docx") && DOCX.equals(contentType)))) throw badRequest();
        if (extension.equals("pdf") && !startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) throw badRequest();
        if (extension.equals("docx") && !startsWith(bytes, new byte[] {'P', 'K', 3, 4})) throw badRequest();
        try {
            if (scanner.scan(bytes) != MalwareScanner.Result.CLEAN)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Attachment did not pass security check");
        } catch (ResponseStatusException expected) {
            throw expected;
        } catch (RuntimeException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Attachment could not be checked; no lead was created");
        }
        return new PreparedAttachment(fileName, contentType, bytes.clone(), sha256(bytes));
    }

    @Transactional
    public LeadAttachment persist(UUID ownerId, UUID leadId, PreparedAttachment prepared) {
        String objectKey = "partner-leads/" + leadId + "/" + UUID.randomUUID();
        try {
            storage.put(objectKey, prepared.bytes(), prepared.contentType());
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Attachment could not be saved; no lead was created");
        }
        LeadAttachment attachment = new LeadAttachment(UUID.randomUUID(), leadId, ownerId, prepared.fileName(),
                prepared.contentType(), prepared.bytes().length, prepared.sha256(), objectKey, LeadAttachmentStatus.CLEAN);
        try {
            return attachments.save(attachment);
        } catch (RuntimeException failure) {
            try { storage.delete(objectKey); } catch (RuntimeException ignored) { }
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public List<LeadAttachment> forLead(UUID leadId) {
        return attachments.findByLead(leadId).stream()
                .filter(file -> file.status() == LeadAttachmentStatus.CLEAN)
                .toList();
    }

    @Transactional(readOnly = true)
    public Download download(UUID leadId, UUID attachmentId, UUID ownerId) {
        LeadAttachment attachment = attachments.findByLeadAndId(leadId, attachmentId)
                .filter(file -> file.ownerId().equals(ownerId))
                .filter(file -> file.status() == LeadAttachmentStatus.CLEAN)
                .orElseThrow(LeadDocumentService::notFound);
        try {
            return new Download(attachment.fileName(), attachment.contentType(), storage.get(attachment.objectKey()));
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Attachment is temporarily unavailable");
        }
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank() || value.length() > 512) throw badRequest();
        String fileName = value.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 255 || fileName.chars().anyMatch(c -> c < 32 || c == 127))
            throw badRequest();
        return fileName;
    }

    private static String normalizeContentType(String value) {
        return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
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
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment must be a PDF or DOCX file up to 20 MB");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
    }

    public record PreparedAttachment(String fileName, String contentType, byte[] bytes, String sha256) {
        public PreparedAttachment { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    public record Download(String fileName, String contentType, byte[] bytes) {
        public Download { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
