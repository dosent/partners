package com.simbirsoft.partners.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Минимальный результат проверки MCP-ключа")
public record PartnerMcpAccessResponse(UUID keyId, boolean active) { }
