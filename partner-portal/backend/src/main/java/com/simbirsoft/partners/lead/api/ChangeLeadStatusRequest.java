package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.lead.domain.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Новое состояние лида, разрешённое переходом")
public record ChangeLeadStatusRequest(@NotNull LeadStatus status) { }
