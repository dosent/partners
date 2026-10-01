package com.simbirsoft.partners.partner.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_application_consent", uniqueConstraints = @UniqueConstraint(
        name = "uq_partner_application_consent_type", columnNames = {"revision_id", "consent_type"}))
public class PartnerApplicationConsentRecord {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PartnerApplicationRevisionRecord revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(nullable = false, length = 8)
    private String locale;

    @Column(name = "accepted_version", nullable = false, length = 100)
    private String acceptedVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    protected PartnerApplicationConsentRecord() {
    }

    PartnerApplicationConsentRecord(PartnerApplicationRevisionRecord revision, ConsentType consentType,
                                    String locale, String acceptedVersion, Instant acceptedAt) {
        this.id = UUID.randomUUID();
        this.revision = revision;
        this.consentType = consentType;
        this.locale = locale;
        this.acceptedVersion = acceptedVersion;
        this.acceptedAt = acceptedAt;
    }

    public ConsentType getConsentType() {
        return consentType;
    }

    public String getLocale() {
        return locale;
    }

    public String getAcceptedVersion() {
        return acceptedVersion;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
