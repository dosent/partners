package com.simbirsoft.partners.partner.domain;

import java.time.Instant;

public record LegalConsent(
        String locale,
        String offerVersion,
        Instant offerAcceptedAt,
        String privacyNoticeVersion,
        Instant privacyAcceptedAt) {

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
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
