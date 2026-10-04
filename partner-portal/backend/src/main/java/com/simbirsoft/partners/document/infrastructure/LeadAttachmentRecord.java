package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.domain.LeadAttachment;
import com.simbirsoft.partners.document.domain.LeadAttachmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_lead_attachment")
class LeadAttachmentRecord {
    @Id UUID id;
    @Column(name = "lead_id", nullable = false, updatable = false) UUID leadId;
    @Column(name = "owner_profile_id", nullable = false, updatable = false) UUID ownerProfileId;
    @Column(name = "file_name", nullable = false, length = 255) String fileName;
    @Column(name = "content_type", nullable = false, length = 127) String contentType;
    @Column(name = "size_bytes", nullable = false) long sizeBytes;
    @Column(nullable = false, length = 64) String sha256;
    @Column(name = "object_key", nullable = false, unique = true, length = 512) String objectKey;
    @Column(nullable = false, length = 32) String status;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;

    protected LeadAttachmentRecord() { }
    static LeadAttachmentRecord from(LeadAttachment file) {
        var record = new LeadAttachmentRecord();
        record.id = file.id(); record.leadId = file.leadId(); record.ownerProfileId = file.ownerId();
        record.fileName = file.fileName(); record.contentType = file.contentType(); record.sizeBytes = file.sizeBytes();
        record.sha256 = file.sha256(); record.objectKey = file.objectKey(); record.status = file.status().name();
        record.createdAt = Instant.now();
        return record;
    }
    LeadAttachment toDomain() {
        return new LeadAttachment(id, leadId, ownerProfileId, fileName, contentType, sizeBytes, sha256, objectKey,
                LeadAttachmentStatus.valueOf(status));
    }
}
