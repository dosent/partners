package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import com.simbirsoft.partners.staffing.domain.StaffingRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record StaffingRequestResponse(UUID id, String number, UUID leadId, StaffingRequest.RoleRequirements role,
        StaffingRequest.WorkConditions work, StaffingRequest.SelectionRequirements selection,
        StaffingRequestStatus status, Instant createdAt) {
    static StaffingRequestResponse from(StaffingRequest request) {
        return new StaffingRequestResponse(request.id(), request.number(), request.leadId(), request.role(),
                request.work(), request.selection(), request.status(), request.createdAt());
    }
}
