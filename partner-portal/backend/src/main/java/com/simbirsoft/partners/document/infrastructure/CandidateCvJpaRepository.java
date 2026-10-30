package com.simbirsoft.partners.document.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CandidateCvJpaRepository extends JpaRepository<CandidateCvRecord, UUID> {
    Optional<CandidateCvRecord> findFirstByCandidateIdOrderByVersionDesc(UUID candidateId);
    Optional<CandidateCvRecord> findFirstByCandidateIdAndStatusOrderByVersionDesc(UUID candidateId, String status);
}
