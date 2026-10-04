package com.simbirsoft.partners.partner.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Активный профиль, созданный из одобренной редакции заявки. */
public record PartnerProfile(UUID id, String identityProvider, String externalSubject,
        UUID applicationId, int approvedRevisionNumber, Instant approvedAt,
        Instant createdAt, Instant updatedAt, byte[] encryptedContactOverrides, String locale,
        boolean emailNotificationsEnabled, boolean inAppNotificationsEnabled) {
    public PartnerProfile {
        Objects.requireNonNull(id, "id is required");
        requireText(identityProvider, "identityProvider");
        requireText(externalSubject, "externalSubject");
        Objects.requireNonNull(applicationId, "applicationId is required");
        if (approvedRevisionNumber < 1) throw new IllegalArgumentException("approvedRevisionNumber must be positive");
        Objects.requireNonNull(approvedAt, "approvedAt is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (locale == null || !java.util.Set.of("RU", "KZ", "EN").contains(locale)) {
            throw new IllegalArgumentException("locale must be RU, KZ or EN");
        }
        encryptedContactOverrides = encryptedContactOverrides == null ? null : encryptedContactOverrides.clone();
    }

    @Override
    public byte[] encryptedContactOverrides() {
        return encryptedContactOverrides == null ? null : encryptedContactOverrides.clone();
    }

    public static PartnerProfile approved(String provider, String subject, UUID applicationId,
            int revisionNumber, Instant approvedAt) {
        return approved(provider, subject, applicationId, revisionNumber, approvedAt, "RU");
    }

    public static PartnerProfile approved(String provider, String subject, UUID applicationId,
            int revisionNumber, Instant approvedAt, String locale) {
        return new PartnerProfile(UUID.randomUUID(), provider, subject, applicationId, revisionNumber,
                approvedAt, approvedAt, approvedAt, null, locale, true, true);
    }

    public PartnerProfile refresh(String provider, String subject, UUID sourceApplicationId,
            int revisionNumber, Instant decisionAt) {
        if (!applicationId.equals(sourceApplicationId)
                || !identityProvider.equals(provider) || !externalSubject.equals(subject)) {
            throw new IllegalArgumentException("Profile ownership cannot be changed");
        }
        return new PartnerProfile(id, identityProvider, externalSubject, applicationId, revisionNumber,
                decisionAt, createdAt, decisionAt, encryptedContactOverrides, locale,
                emailNotificationsEnabled, inAppNotificationsEnabled);
    }

    public PartnerProfile withSettings(byte[] encryptedContacts, String updatedLocale, boolean emailNotifications,
            boolean inAppNotifications, Instant changedAt) {
        return new PartnerProfile(id, identityProvider, externalSubject, applicationId, approvedRevisionNumber,
                approvedAt, createdAt, changedAt, encryptedContacts, updatedLocale, emailNotifications,
                inAppNotifications);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and normalized");
        }
    }
}
