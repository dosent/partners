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

    @Column(name = "assigned_manager_username", length = 255)
    private String assignedManagerUsername;

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
        record.assignedManagerUsername = application.assignedManagerUsername();
        record.appendRevisions(application.revisions(), 0, protector);
        record.appendStatusEvents(application.statusHistory(), 0);
        return record;
    }

    public void updateFrom(PartnerApplication application, RegistrationDataProtector protector) {
        if (!id.equals(application.id()) || !identityProvider.equals(application.identityProvider())
                || !externalSubject.equals(application.externalSubject())) {
            throw new IllegalArgumentException("Cannot update a different application");
        }
        if (revisions.size() > application.revisions().size()
                || statusHistory.size() > application.statusHistory().size()) {
            throw new IllegalArgumentException("Application history cannot be removed");
        }
        var persisted = toDomain(protector);
        if (!persisted.revisions().equals(application.revisions().subList(0, revisions.size()))
                || !persisted.statusHistory().equals(application.statusHistory().subList(0, statusHistory.size()))) {
            throw new IllegalArgumentException("Existing application history cannot be changed");
        }
        appendRevisions(application.revisions(), revisions.size(), protector);
        appendStatusEvents(application.statusHistory(), statusHistory.size());
        status = application.status();
        assignedManagerUsername = application.assignedManagerUsername();
    }

    private void appendRevisions(List<PartnerApplicationRevision> source, int fromIndex,
                                 RegistrationDataProtector protector) {
        source.subList(fromIndex, source.size()).forEach(revision -> {
            var protectedData = protector.protect(revision.registrationData());
            var revisionRecord = new PartnerApplicationRevisionRecord(this, revision.revisionNumber(),
                    protectedData.encryptedPayload(), protectedData.taxIdentifierHash(), revision.submittedAt());
            revisionRecord.addConsent(new PartnerApplicationConsentRecord(revisionRecord, ConsentType.OFFER,
                    revision.consent().locale(), revision.consent().offerVersion(),
                    revision.consent().offerAcceptedAt()));
            revisionRecord.addConsent(new PartnerApplicationConsentRecord(revisionRecord,
                    ConsentType.PERSONAL_DATA_PROCESSING, revision.consent().locale(),
                    revision.consent().privacyNoticeVersion(), revision.consent().privacyAcceptedAt()));
            revisions.add(revisionRecord);
        });
    }

    private void appendStatusEvents(List<PartnerApplicationStatusEvent> source, int fromIndex) {
        for (int index = fromIndex; index < source.size(); index++) {
            var event = source.get(index);
            statusHistory.add(new PartnerApplicationStatusRecord(this, index + 1,
                    event.status(), event.changedBy(), event.reason(), event.changedAt()));
        }
    }

    public PartnerApplication toDomain(RegistrationDataProtector protector) {
        return PartnerApplication.restore(id, identityProvider, externalSubject, status, assignedManagerUsername,
                revisions.stream().map(revision -> revision.toDomain(protector)).toList(),
                statusHistory.stream().map(PartnerApplicationStatusRecord::toDomain).toList());
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

    public String getAssignedManagerUsername() { return assignedManagerUsername; }

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
