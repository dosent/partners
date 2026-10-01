package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.partner.domain.LegalConsent;
import com.simbirsoft.partners.partner.domain.PartnerApplicationRevision;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "partner_application_revision", uniqueConstraints = @UniqueConstraint(
        name = "uq_partner_application_revision_number", columnNames = {"application_id", "revision_number"}))
public class PartnerApplicationRevisionRecord {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PartnerApplicationRecord application;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "bytea")
    private byte[] encryptedPayload;

    @Column(name = "tax_identifier_hash", length = 64)
    private String taxIdentifierHash;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL)
    @OrderBy("consentType ASC")
    private List<PartnerApplicationConsentRecord> consents = new ArrayList<>();

    protected PartnerApplicationRevisionRecord() {
    }

    PartnerApplicationRevisionRecord(PartnerApplicationRecord application, int revisionNumber,
                                     byte[] encryptedPayload, String taxIdentifierHash, Instant submittedAt) {
        this.id = UUID.randomUUID();
        this.application = application;
        this.revisionNumber = revisionNumber;
        this.encryptedPayload = encryptedPayload.clone();
        this.taxIdentifierHash = taxIdentifierHash;
        this.submittedAt = submittedAt;
    }

    void addConsent(PartnerApplicationConsentRecord consent) {
        consents.add(consent);
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public byte[] getEncryptedPayload() {
        return encryptedPayload.clone();
    }

    public String getTaxIdentifierHash() {
        return taxIdentifierHash;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public List<PartnerApplicationConsentRecord> getConsents() {
        return List.copyOf(consents);
    }

    PartnerApplicationRevision toDomain(RegistrationDataProtector protector) {
        var offer = consents.stream().filter(consent -> consent.getConsentType() == ConsentType.OFFER)
                .findFirst().orElseThrow(() -> new IllegalStateException("Offer acceptance is missing"));
        var privacy = consents.stream()
                .filter(consent -> consent.getConsentType() == ConsentType.PERSONAL_DATA_PROCESSING)
                .findFirst().orElseThrow(() -> new IllegalStateException("Privacy consent is missing"));
        if (!offer.getLocale().equals(privacy.getLocale())) {
            throw new IllegalStateException("Consent locales do not match");
        }
        var consent = new LegalConsent(offer.getLocale(), offer.getAcceptedVersion(), offer.getAcceptedAt(),
                privacy.getAcceptedVersion(), privacy.getAcceptedAt());
        var protectedData = new ProtectedRegistrationData(encryptedPayload, taxIdentifierHash);
        return new PartnerApplicationRevision(revisionNumber, protector.reveal(protectedData), consent, submittedAt);
    }
}
