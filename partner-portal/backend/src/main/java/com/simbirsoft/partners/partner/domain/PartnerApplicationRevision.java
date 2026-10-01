package com.simbirsoft.partners.partner.domain;

import java.time.Instant;
import java.util.Objects;

public record PartnerApplicationRevision(
        int revisionNumber,
        PartnerRegistrationData registrationData,
        LegalConsent consent,
        Instant submittedAt) {

    public PartnerApplicationRevision {
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revisionNumber must be positive");
        }
        Objects.requireNonNull(registrationData, "registrationData is required");
        Objects.requireNonNull(consent, "consent is required");
        Objects.requireNonNull(submittedAt, "submittedAt is required");
    }
}
