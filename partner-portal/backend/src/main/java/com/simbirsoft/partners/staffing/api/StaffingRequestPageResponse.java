package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import java.util.List;

public record StaffingRequestPageResponse(List<StaffingRequestResponse> items, int page, int size) {
    static StaffingRequestPageResponse from(List<StaffingRequest> values, int page, int size) {
        return new StaffingRequestPageResponse(values.stream().map(StaffingRequestResponse::from).toList(), page, size);
    }
}
