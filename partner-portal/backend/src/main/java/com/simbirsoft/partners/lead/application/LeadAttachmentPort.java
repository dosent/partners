package com.simbirsoft.partners.lead.application;

import java.util.List;
import java.util.UUID;

/** Application contract for the document module's lead attachment use cases. */
public interface LeadAttachmentPort {
    long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    PreparedAttachment prepare(String fileName, String contentType, byte[] content);

    Attachment persist(UUID ownerId, UUID leadId, PreparedAttachment attachment);

    List<Attachment> forLead(UUID leadId);

    Download download(UUID leadId, UUID attachmentId, UUID ownerId);

    record PreparedAttachment(String fileName, String contentType, byte[] content, String sha256) {
        public PreparedAttachment { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }

    record Attachment(UUID id, UUID leadId, String fileName, String scanStatus) { }

    record Download(String fileName, String contentType, byte[] content) {
        public Download { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
