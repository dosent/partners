package com.simbirsoft.partners.staffing.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

interface StaffingRequestJpaRepository extends JpaRepository<StaffingRequestRecord, UUID> {
    Optional<StaffingRequestRecord> findByIdAndOwnerProfileId(UUID id, UUID ownerProfileId);
    Optional<StaffingRequestRecord> findByOwnerProfileIdAndIdempotencyKey(UUID ownerProfileId, UUID idempotencyKey);
    List<StaffingRequestRecord> findByOwnerProfileIdOrderByCreatedAtDesc(UUID ownerProfileId, PageRequest page);
}
