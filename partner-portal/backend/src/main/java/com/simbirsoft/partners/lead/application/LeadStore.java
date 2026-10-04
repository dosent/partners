package com.simbirsoft.partners.lead.application;

import com.simbirsoft.partners.lead.domain.Lead;
import com.simbirsoft.partners.lead.domain.LeadMessage;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import com.simbirsoft.partners.lead.domain.LeadStatusEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadStore {
    Lead save(Lead lead);
    Optional<Lead> findOwned(UUID id, UUID ownerProfileId);
    Optional<Lead> findAny(UUID id);
    Lead changeStatus(UUID id, LeadStatus expectedStatus, LeadStatus nextStatus, String changedBy, Instant changedAt);
    Page findPage(UUID ownerProfileId, List<LeadStatus> statuses, String query, Instant updatedFrom, Instant updatedTo,
            Instant beforeUpdatedAt, UUID beforeId, int limit);
    long count(UUID ownerProfileId, List<LeadStatus> statuses, String query, Instant updatedFrom, Instant updatedTo);
    List<Lead> findAllOwned(UUID ownerProfileId);
    List<LeadMessage> publicMessages(UUID leadId);
    List<LeadStatusEvent> statusHistory(UUID leadId);
    LeadMessage addMessage(UUID leadId, UUID ownerProfileId, String body, Instant createdAt);
    record Page(List<Lead> items, boolean hasMore) { }
}
