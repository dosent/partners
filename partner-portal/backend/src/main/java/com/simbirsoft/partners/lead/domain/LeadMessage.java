package com.simbirsoft.partners.lead.domain;

import java.time.Instant;
import java.util.UUID;

public record LeadMessage(UUID id, UUID leadId, String authorType, String body, boolean internal, Instant createdAt,
        String authorName) {
    public LeadMessage(UUID id, UUID leadId, String authorType, String body, boolean internal, Instant createdAt) {
        this(id, leadId, authorType, body, internal, createdAt, null);
    }
}
