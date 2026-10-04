package com.simbirsoft.partners.lead.domain;

import java.time.Instant;
import java.util.UUID;

public record LeadMessage(UUID id, UUID leadId, String authorType, String body, boolean internal, Instant createdAt) { }
