package com.simbirsoft.partners.staffing.application;

import java.util.UUID;
import java.util.List;

public interface StaffingRequestAttachmentPort {
    Attachment upload(UUID ownerId, UUID requestId, String kind, String fileName, String contentType, byte[] content);
    Download download(UUID ownerId, UUID requestId, UUID attachmentId);
    List<Attachment> list(UUID ownerId, UUID requestId);
    record Attachment(UUID id, String kind, String fileName) { }
    record Download(String fileName, String contentType, byte[] content) {
        public Download { content=content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
