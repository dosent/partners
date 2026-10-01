package com.simbirsoft.partners.access.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Текущая внешняя идентичность партнёра")
public record PartnerSessionResponse(String provider, String subject) {
}
