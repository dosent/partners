package com.simbirsoft.partners.staffing.application;

import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffingRequestStore {
    StaffingRequest save(StaffingRequest request);
    StaffingRequest saveAndFlush(StaffingRequest request);
    Optional<StaffingRequest> findAny(UUID requestId);
    StaffPage findStaffPage(int page, int size);
    Optional<StaffingRequest> findOwned(UUID requestId, UUID ownerProfileId);
    List<StaffingRequest> findOwnedPage(UUID ownerProfileId, int page, int size);
    Optional<StaffingRequest> findByIdempotencyKey(UUID ownerProfileId, UUID idempotencyKey);
    record StaffPage(List<StaffingRequest> items, int page, int size, long total) { }
}
