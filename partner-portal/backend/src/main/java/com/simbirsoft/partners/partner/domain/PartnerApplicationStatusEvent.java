package com.simbirsoft.partners.partner.domain;

import java.time.Instant;
import java.util.Objects;

public record PartnerApplicationStatusEvent(
        PartnerApplicationStatus status,
        String changedBy,
        String reason,
        Instant changedAt) {

    public PartnerApplicationStatusEvent {
        Objects.requireNonNull(status, "status is required");
        if (changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("changedBy is required");
        }
        Objects.requireNonNull(changedAt, "changedAt is required");
    }
}
