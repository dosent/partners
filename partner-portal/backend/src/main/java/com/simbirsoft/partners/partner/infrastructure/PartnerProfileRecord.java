package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.partner.domain.PartnerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uq_partner_profile_identity", columnNames = {"identity_provider", "external_subject"}),
        @UniqueConstraint(name = "uq_partner_profile_application", columnNames = "application_id")
})
public class PartnerProfileRecord {
    @Id
    private UUID id;

    @Column(name = "identity_provider", nullable = false, updatable = false, length = 32)
    private String identityProvider;

    @Column(name = "external_subject", nullable = false, updatable = false, length = 255)
    private String externalSubject;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "approved_revision_number", nullable = false)
    private int approvedRevisionNumber;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "encrypted_contact_overrides", columnDefinition = "bytea")
    private byte[] encryptedContactOverrides;

    @Column(name = "locale", nullable = false, length = 2)
    private String locale;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled;

    @Column(name = "in_app_notifications_enabled", nullable = false)
    private boolean inAppNotificationsEnabled;

    @Version
    @Column(nullable = false)
    private long version;

    protected PartnerProfileRecord() { }

    static PartnerProfileRecord from(PartnerProfile profile) {
        var record = new PartnerProfileRecord();
        record.id = profile.id();
        record.identityProvider = profile.identityProvider();
        record.externalSubject = profile.externalSubject();
        record.applicationId = profile.applicationId();
        record.approvedRevisionNumber = profile.approvedRevisionNumber();
        record.approvedAt = profile.approvedAt();
        record.createdAt = profile.createdAt();
        record.updatedAt = profile.updatedAt();
        record.encryptedContactOverrides = profile.encryptedContactOverrides();
        record.locale = profile.locale();
        record.emailNotificationsEnabled = profile.emailNotificationsEnabled();
        record.inAppNotificationsEnabled = profile.inAppNotificationsEnabled();
        return record;
    }

    void updateFrom(PartnerProfile profile) {
        if (!id.equals(profile.id()) || !identityProvider.equals(profile.identityProvider())
                || !externalSubject.equals(profile.externalSubject()) || !applicationId.equals(profile.applicationId())) {
            throw new IllegalArgumentException("Cannot change partner profile ownership");
        }
        approvedRevisionNumber = profile.approvedRevisionNumber();
        approvedAt = profile.approvedAt();
        updatedAt = profile.updatedAt();
        encryptedContactOverrides = profile.encryptedContactOverrides();
        locale = profile.locale();
        emailNotificationsEnabled = profile.emailNotificationsEnabled();
        inAppNotificationsEnabled = profile.inAppNotificationsEnabled();
    }

    PartnerProfile toDomain() {
        return new PartnerProfile(id, identityProvider, externalSubject, applicationId,
                approvedRevisionNumber, approvedAt, createdAt, updatedAt, encryptedContactOverrides, locale,
                emailNotificationsEnabled, inAppNotificationsEnabled);
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public int getApprovedRevisionNumber() { return approvedRevisionNumber; }
}
