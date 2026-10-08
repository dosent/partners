package com.simbirsoft.partners.staffing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description="Команда изменения ответственного")
public record StaffingRequestAssignmentRequest(
        @Schema(description="Username Resource Manager; null снимает назначение", nullable=true) @Size(max=255) String username,
        @Schema(description="Версия заявки из очереди", requiredMode=Schema.RequiredMode.REQUIRED) @NotNull @Min(0) Long expectedVersion) { }
