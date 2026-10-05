package com.simbirsoft.partners.lead.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Комментарий сотрудника; internal=true оставляет его только сотрудникам")
public record AdminLeadMessageRequest(@NotBlank @Size(max = 5000) String body, boolean internal) { }
