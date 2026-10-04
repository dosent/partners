package com.simbirsoft.partners.audit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Событие аудита без значений контактных данных. */
@Entity
@Table(name = "partner_profile_audit")
public class PartnerProfileAuditRecord {
    @Id
    private UUID id;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;

    @Column(name = "identity_provider", nullable = false, updatable = false, length = 32)
    private String identityProvider;

    @Column(name = "external_subject", nullable = false, updatable = false, length = 255)
    private String externalSubject;

    @Column(name = "changed_fields", nullable = false, updatable = false, length = 500)
    private String changedFields;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PartnerProfileAuditRecord() { }

    public UUID getProfileId() { return profileId; }
    public String getChangedFields() { return changedFields; }
    public Instant getOccurredAt() { return occurredAt; }

    static PartnerProfileAuditRecord create(UUID profileId, String identityProvider, String externalSubject,
            String changedFields, Instant occurredAt) {
        var record = new PartnerProfileAuditRecord();
        record.id = UUID.randomUUID();
        record.profileId = profileId;
        record.identityProvider = identityProvider;
        record.externalSubject = externalSubject;
        record.changedFields = changedFields;
        record.occurredAt = occurredAt;
        return record;
    }
}
