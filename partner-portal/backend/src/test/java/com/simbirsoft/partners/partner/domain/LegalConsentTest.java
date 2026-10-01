package com.simbirsoft.partners.partner.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatCode;

class LegalConsentTest {
    @Test
    void storesTheAcceptedOfferAndPrivacyTextVersionsAndTimes() {
        assertThatCode(() -> new LegalConsent(
                "ru-RU", "offer-3", Instant.parse("2026-10-01T10:00:00Z"),
                "privacy-5", Instant.parse("2026-10-01T10:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresVersionAndAcceptanceTimeForBothTexts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LegalConsent(
                "ru-RU", "offer-3", null, "privacy-5", Instant.parse("2026-10-01T10:00:00Z")));
    }
}
