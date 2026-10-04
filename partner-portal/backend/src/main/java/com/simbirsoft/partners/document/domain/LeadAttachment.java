package com.simbirsoft.partners.document.domain;

import java.util.UUID;

public record LeadAttachment(UUID id, UUID leadId, UUID ownerId, String fileName, String contentType,
        long sizeBytes, String sha256, String objectKey, LeadAttachmentStatus status) {
}
