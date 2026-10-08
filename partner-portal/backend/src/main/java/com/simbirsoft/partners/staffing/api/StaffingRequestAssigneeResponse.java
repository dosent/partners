package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.access.application.StaffRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="Сотрудник, которого можно назначить ответственным")
public record StaffingRequestAssigneeResponse(String username, StaffRole role) {
    static StaffingRequestAssigneeResponse from(String username) {
        return new StaffingRequestAssigneeResponse(username, StaffRole.RESOURCE_MANAGER);
    }
}
