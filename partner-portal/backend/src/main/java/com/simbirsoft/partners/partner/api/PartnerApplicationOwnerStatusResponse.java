package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Статус собственной заявки партнёра")
public record PartnerApplicationOwnerStatusResponse(
        PartnerApplicationStatus status,
        String latestDecisionReason) { }
