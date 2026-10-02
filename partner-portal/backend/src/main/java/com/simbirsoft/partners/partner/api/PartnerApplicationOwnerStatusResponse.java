package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.partner.domain.PartnerApplicationStatus;
import com.simbirsoft.partners.partner.application.PartnerApplicationSubmissionService.OwnerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Статус собственной заявки партнёра")
public record PartnerApplicationOwnerStatusResponse(
        PartnerApplicationStatus status,
        String latestDecisionReason,
        String locale) {
    public static PartnerApplicationOwnerStatusResponse from(OwnerStatus status) {
        return new PartnerApplicationOwnerStatusResponse(status.status(), status.latestDecisionReason(), status.locale());
    }
}
