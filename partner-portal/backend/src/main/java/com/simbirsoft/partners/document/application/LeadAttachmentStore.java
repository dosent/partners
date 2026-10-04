package com.simbirsoft.partners.document.application;

import com.simbirsoft.partners.document.domain.LeadAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadAttachmentStore {
    LeadAttachment save(LeadAttachment attachment);
    List<LeadAttachment> findByLead(UUID leadId);
    Optional<LeadAttachment> findByLeadAndId(UUID leadId, UUID attachmentId);
}
