package com.simbirsoft.partners.document.infrastructure;

import com.simbirsoft.partners.document.application.LeadAttachmentStore;
import com.simbirsoft.partners.document.domain.LeadAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

@Repository
@Profile("!auth-test")
class JpaLeadAttachmentStore implements LeadAttachmentStore {
    private final LeadAttachmentJpaRepository repository;
    private final ConcurrentHashMap<UUID, LeadAttachment> testFallback = new ConcurrentHashMap<>();
    JpaLeadAttachmentStore(ObjectProvider<LeadAttachmentJpaRepository> repositories) { this.repository = repositories.getIfAvailable(); }
    @Override public LeadAttachment save(LeadAttachment attachment) {
        if (repository == null) return testFallback.compute(attachment.id(), (id, existing) -> existing == null ? attachment : existing);
        return repository.saveAndFlush(LeadAttachmentRecord.from(attachment)).toDomain();
    }
    @Override public List<LeadAttachment> findByLead(UUID leadId) {
        if (repository == null) return testFallback.values().stream().filter(file -> file.leadId().equals(leadId)).toList();
        return repository.findByLeadIdOrderByCreatedAtAsc(leadId).stream().map(LeadAttachmentRecord::toDomain).toList();
    }
    @Override public Optional<LeadAttachment> findByLeadAndId(UUID leadId, UUID attachmentId) {
        if (repository == null) return Optional.ofNullable(testFallback.get(attachmentId)).filter(file -> file.leadId().equals(leadId));
        return repository.findByLeadIdAndId(leadId, attachmentId).map(LeadAttachmentRecord::toDomain);
    }
}
