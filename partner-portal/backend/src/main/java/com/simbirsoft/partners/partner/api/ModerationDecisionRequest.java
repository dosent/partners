package com.simbirsoft.partners.partner.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Причина отклонения или запроса изменений")
public record ModerationDecisionRequest(
        @NotBlank @Size(max = 1000) @Pattern(regexp = "^[^\\p{Cc}]+$") String reason) { }
