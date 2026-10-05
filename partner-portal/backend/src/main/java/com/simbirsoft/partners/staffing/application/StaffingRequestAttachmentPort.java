package com.simbirsoft.partners.staffing.application;

import java.util.UUID;

public interface StaffingRequestAttachmentPort {
    Attachment upload(UUID ownerId, UUID requestId, String kind, String fileName, String contentType, byte[] content);
    Download download(UUID ownerId, UUID requestId, UUID attachmentId);
    record Attachment(UUID id, String kind, String fileName) { }
    record Download(String fileName, String contentType, byte[] content) {
        public Download { content=content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
