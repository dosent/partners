package com.simbirsoft.partners.lead.api;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
@Schema(description="Сообщение менеджеру по собственному лиду")
public record LeadMessageRequest(@NotBlank @Size(max=5000) String body) { }
