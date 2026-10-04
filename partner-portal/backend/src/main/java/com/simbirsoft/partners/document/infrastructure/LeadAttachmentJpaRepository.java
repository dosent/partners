package com.simbirsoft.partners.document.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadAttachmentJpaRepository extends JpaRepository<LeadAttachmentRecord, UUID> {
    List<LeadAttachmentRecord> findByLeadIdOrderByCreatedAtAsc(UUID leadId);
    Optional<LeadAttachmentRecord> findByLeadIdAndId(UUID leadId, UUID id);
}
