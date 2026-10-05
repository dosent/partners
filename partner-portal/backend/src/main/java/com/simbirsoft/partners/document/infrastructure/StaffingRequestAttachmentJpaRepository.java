package com.simbirsoft.partners.document.infrastructure;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface StaffingRequestAttachmentJpaRepository extends JpaRepository<StaffingRequestAttachmentRecord, UUID> {
    Optional<StaffingRequestAttachmentRecord> findByIdAndRequestIdAndOwnerProfileId(UUID id, UUID requestId, UUID ownerProfileId);
    Optional<StaffingRequestAttachmentRecord> findByRequestIdAndOwnerProfileIdAndKind(UUID requestId, UUID ownerProfileId, String kind);
    List<StaffingRequestAttachmentRecord> findAllByRequestIdAndOwnerProfileIdOrderByKindAsc(UUID requestId, UUID ownerProfileId);
}
