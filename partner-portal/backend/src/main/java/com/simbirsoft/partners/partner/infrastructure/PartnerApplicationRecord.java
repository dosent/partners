package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.partner.domain.PartnerApplication;
import com.simbirsoft.partners.partner.domain.PartnerApplicationRevision;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatusEvent;
import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "partner_application", uniqueConstraints = @UniqueConstraint(
        name = "uq_partner_application_identity", columnNames = {"identity_provider", "external_subject"}))
public class PartnerApplicationRecord {
    @Id
    private UUID id;

    @Column(name = "identity_provider", nullable = false, updatable = false, length = 32)
    private String identityProvider;

    @Column(name = "external_subject", nullable = false, updatable = false, length = 255)
    private String externalSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerApplicationStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL)
    @OrderBy("revisionNumber ASC")
    private List<PartnerApplicationRevisionRecord> revisions = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL)
    @OrderBy("eventNumber ASC")
    private List<PartnerApplicationStatusRecord> statusHistory = new ArrayList<>();

    protected PartnerApplicationRecord() {
    }

    private PartnerApplicationRecord(UUID id, String identityProvider, String externalSubject,
                                     PartnerApplicationStatus status) {
        this.id = id;
        this.identityProvider = identityProvider;
        this.externalSubject = externalSubject;
        this.status = status;
    }

    public static PartnerApplicationRecord from(
            PartnerApplication application, RegistrationDataProtector protector) {
        var record = new PartnerApplicationRecord(application.id(), application.identityProvider(),
                application.externalSubject(), application.status());
        application.revisions().forEach(revision -> {
            var protectedData = protector.protect(revision.registrationData());
            var revisionRecord = new PartnerApplicationRevisionRecord(record, revision.revisionNumber(),
                    protectedData.encryptedPayload(), protectedData.taxIdentifierHash(), revision.submittedAt());
            revisionRecord.addConsent(new PartnerApplicationConsentRecord(revisionRecord, ConsentType.OFFER,
                    revision.consent().locale(), revision.consent().offerVersion(),
                    revision.consent().offerAcceptedAt()));
            revisionRecord.addConsent(new PartnerApplicationConsentRecord(revisionRecord,
                    ConsentType.PERSONAL_DATA_PROCESSING, revision.consent().locale(),
                    revision.consent().privacyNoticeVersion(), revision.consent().privacyAcceptedAt()));
            record.revisions.add(revisionRecord);
        });
        for (int index = 0; index < application.statusHistory().size(); index++) {
            var event = application.statusHistory().get(index);
            record.statusHistory.add(new PartnerApplicationStatusRecord(record, index + 1,
                    event.status(), event.changedBy(), event.reason(), event.changedAt()));
        }
        return record;
    }

    public PartnerApplication toDomain(RegistrationDataProtector protector) {
        return PartnerApplication.restore(id, identityProvider, externalSubject, status,
                revisions.stream().map(revision -> revision.toDomain(protector)).toList(),
                statusHistory.stream().map(PartnerApplicationStatusRecord::toDomain).toList());
    }

    public void changeStatus(int eventNumber, PartnerApplicationStatus nextStatus, String changedBy,
                             String reason, java.time.Instant changedAt) {
        status = nextStatus;
        statusHistory.add(new PartnerApplicationStatusRecord(this, eventNumber,
                nextStatus, changedBy, reason, changedAt));
    }

    public UUID getId() {
        return id;
    }

    public String getIdentityProvider() {
        return identityProvider;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public PartnerApplicationStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public List<PartnerApplicationRevisionRecord> getRevisions() {
        return List.copyOf(revisions);
    }

    public List<PartnerApplicationStatusRecord> getStatusHistory() {
        return List.copyOf(statusHistory);
    }
}
