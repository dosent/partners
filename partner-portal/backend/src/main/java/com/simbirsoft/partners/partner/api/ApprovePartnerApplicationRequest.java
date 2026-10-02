package com.simbirsoft.partners.partner.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Необязательное назначение ответственного менеджера")
public record ApprovePartnerApplicationRequest(
        @Size(max = 255) String assignedManagerUsername) { }
