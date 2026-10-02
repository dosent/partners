package com.simbirsoft.partners.partner.domain;

import java.time.Instant;

public record LegalConsent(
        String locale,
        String offerVersion,
        Instant offerAcceptedAt,
        String privacyNoticeVersion,
        Instant privacyAcceptedAt,
        int contentVersion,
        String contentSha256) {

    /** Compatibility constructor for historical callers that predate publication snapshots. */
    public LegalConsent(String locale, String offerVersion, Instant offerAcceptedAt,
                        String privacyNoticeVersion, Instant privacyAcceptedAt) {
        this(locale, offerVersion, offerAcceptedAt, privacyNoticeVersion, privacyAcceptedAt, 0, null);
    }

    public LegalConsent {
        requireText(locale, "locale");
        requireText(offerVersion, "offerVersion");
        if (offerAcceptedAt == null) {
            throw new IllegalArgumentException("offerAcceptedAt is required");
        }
        requireText(privacyNoticeVersion, "privacyNoticeVersion");
        if (privacyAcceptedAt == null) {
            throw new IllegalArgumentException("privacyAcceptedAt is required");
        }
        if (contentVersion < 0 || (contentVersion == 0 && contentSha256 != null)
                || (contentVersion > 0 && (contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")))) {
            throw new IllegalArgumentException("Published content snapshot reference is invalid");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
