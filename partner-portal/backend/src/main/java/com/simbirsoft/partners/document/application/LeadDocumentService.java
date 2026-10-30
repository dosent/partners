package com.simbirsoft.partners.document.application;

import com.simbirsoft.partners.document.domain.LeadAttachment;
import com.simbirsoft.partners.document.domain.LeadAttachmentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("!auth-test")
public class LeadDocumentService {
    public static final int MAX_FILE_SIZE_BYTES = DocumentValidationService.MAX_FILE_SIZE_BYTES;
    private final LeadAttachmentStore attachments;
    private final ObjectStorage storage;
    private final DocumentValidationService validation;

    public LeadDocumentService(LeadAttachmentStore attachments, ObjectStorage storage, MalwareScanner scanner) {
        this.attachments = attachments;
        this.storage = storage;
        this.validation = new DocumentValidationService(scanner);
    }

    /** Validate and scan before any lead, metadata or object storage write is performed. */
    public PreparedAttachment prepare(String originalFileName, String suppliedContentType, byte[] bytes) {
        try {
            var prepared = validation.prepare(originalFileName, suppliedContentType, bytes);
            return new PreparedAttachment(prepared.fileName(), prepared.contentType(), prepared.bytes(), prepared.sha256());
        } catch (ResponseStatusException expected) {
            // Preserve the existing lead API while the generic CV validator uses HTTP 413.
            if (expected.getStatusCode().value() == 413) throw badRequest();
            if (expected.getStatusCode().is5xxServerError())
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Attachment could not be checked; no lead was created");
            throw expected;
        }
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
