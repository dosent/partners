package com.simbirsoft.partners.lead.infrastructure;
import java.util.List; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface LeadMessageJpaRepository extends JpaRepository<LeadMessageRecord,UUID> {
    List<LeadMessageRecord> findByLeadIdOrderByCreatedAtAsc(UUID leadId);
}
