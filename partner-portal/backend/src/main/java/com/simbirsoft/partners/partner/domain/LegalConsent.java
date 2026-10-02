package com.simbirsoft.partners.partner.domain;

import java.time.Instant;

public record LegalConsent(
        String locale,
        String offerVersion,
        Instant offerAcceptedAt,
        String privacyNoticeVersion,
        Instant privacyAcceptedAt,
        int contentVersion,
        String contentSha256,
        String offerSha256,
        String privacyNoticeSha256) {

    /** Compatibility constructor for historical callers that predate publication snapshots. */
    public LegalConsent(String locale, String offerVersion, Instant offerAcceptedAt,
                        String privacyNoticeVersion, Instant privacyAcceptedAt) {
        this(locale, offerVersion, offerAcceptedAt, privacyNoticeVersion, privacyAcceptedAt, 0, null, null, null);
    }

    /** Compatibility for previously stored snapshots. */
    public LegalConsent(String locale, String offerVersion, Instant offerAcceptedAt,
                        String privacyNoticeVersion, Instant privacyAcceptedAt,
                        int contentVersion, String contentSha256) {
        this(locale, offerVersion, offerAcceptedAt, privacyNoticeVersion, privacyAcceptedAt,
                contentVersion, contentSha256, null, null);
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
        if ((offerSha256 == null) != (privacyNoticeSha256 == null)
                || (offerSha256 != null && (!offerSha256.matches("[0-9a-f]{64}")
                || !privacyNoticeSha256.matches("[0-9a-f]{64}")))) {
            throw new IllegalArgumentException("Document snapshot hashes are invalid");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
