package com.simbirsoft.partners.lead.infrastructure;
import java.util.List; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface LeadStatusHistoryJpaRepository extends JpaRepository<LeadStatusHistoryRecord,UUID> {
    List<LeadStatusHistoryRecord> findByLeadIdOrderByChangedAtAsc(UUID leadId);
}
