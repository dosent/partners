package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import com.simbirsoft.partners.staffing.domain.StaffingRequestStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record StaffingRequestResponse(UUID id, String number, UUID leadId, StaffingRequest.RoleRequirements role,
        StaffingRequest.WorkConditions work, StaffingRequest.SelectionRequirements selection,
        StaffingRequestStatus status, Instant createdAt, List<Attachment> attachments) {
    static StaffingRequestResponse from(StaffingRequest request) {
        return new StaffingRequestResponse(request.id(), request.number(), request.leadId(), request.role(),
                request.work(), request.selection(), request.status(), request.createdAt(), List.of());
    }
    static StaffingRequestResponse from(StaffingRequest request, List<com.simbirsoft.partners.staffing.application.StaffingRequestAttachmentPort.Attachment> files) {
        var base = from(request);
        return new StaffingRequestResponse(base.id(), base.number(), base.leadId(), base.role(), base.work(),
                base.selection(), base.status(), base.createdAt(), files.stream()
                .map(file -> new Attachment(file.id(), file.kind(), file.fileName())).toList());
    }
    public record Attachment(UUID id, String kind, String fileName) { }
}
