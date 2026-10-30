package com.simbirsoft.partners.document.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Each row is an immutable file version; candidate optimistic locking serializes replacement. */
@Entity
@Table(name = "candidate_cv", uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "version"}))
class CandidateCvRecord {
    @Id UUID id;
    @Column(name = "candidate_id", nullable = false, updatable = false) UUID candidateId;
    @Column(nullable = false, updatable = false) long version;
    @Column(name = "file_name", nullable = false, updatable = false, length = 255) String fileName;
    @Column(name = "content_type", nullable = false, updatable = false, length = 255) String contentType;
    @Column(nullable = false, updatable = false) long size;
    @Column(nullable = false, updatable = false, length = 64) String sha256;
    @Column(name = "object_key", nullable = false, updatable = false, length = 512) String objectKey;
    @Column(nullable = false, length = 32) String status;
    @Column(name = "created_by", nullable = false, updatable = false, length = 255) String createdBy;
    @Column(name = "checked_at", nullable = false, updatable = false) Instant checkedAt;

    protected CandidateCvRecord() { }

    CandidateCvRecord(UUID id, UUID candidateId, long version, String fileName, String contentType,
            long size, String sha256, String objectKey, String createdBy, Instant checkedAt) {
        this.id = id; this.candidateId = candidateId; this.version = version; this.fileName = fileName;
        this.contentType = contentType; this.size = size; this.sha256 = sha256; this.objectKey = objectKey;
        this.status = "CLEAN"; this.createdBy = createdBy; this.checkedAt = checkedAt;
    }
}
