package com.simbirsoft.partners.lead.application;

import java.util.Optional;
import java.util.UUID;

/** Owner-scoped lead context consumed by other modules without exposing lead persistence types. */
public interface LeadPartnerContextLookup {
    Optional<LeadContext> findOwnedContext(UUID leadId, UUID partnerProfileId);
    record LeadContext(UUID id, String companyName, String requestSummary) { }
}
