package com.simbirsoft.partners.document.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="staffing_request_attachment")
class StaffingRequestAttachmentRecord {
    @Id UUID id;
    @Column(name="request_id", nullable=false, updatable=false) UUID requestId;
    @Column(name="owner_profile_id", nullable=false, updatable=false) UUID ownerProfileId;
    @Column(nullable=false, length=24) String kind;
    @Column(name="file_name", nullable=false, length=255) String fileName;
    @Column(name="content_type", nullable=false, length=100) String contentType;
    @Column(name="object_key", nullable=false, unique=true, length=500) String objectKey;
    @Column(name="created_at", nullable=false) Instant createdAt;
    protected StaffingRequestAttachmentRecord() { }
    StaffingRequestAttachmentRecord(UUID id, UUID requestId, UUID ownerProfileId, String kind,
            String fileName, String contentType, String objectKey) {
        this.id=id; this.requestId=requestId; this.ownerProfileId=ownerProfileId; this.kind=kind;
        this.fileName=fileName; this.contentType=contentType; this.objectKey=objectKey; this.createdAt=Instant.now();
    }
}
