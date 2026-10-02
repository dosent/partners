package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.partner.application.PartnerApplicationOwnerStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partner-applications")
@Tag(name = "Partner applications", description = "Статус собственной заявки партнёра")
public class PartnerApplicationOwnerController {
    private final PartnerApplicationOwnerStatusService service;

    public PartnerApplicationOwnerController(PartnerApplicationOwnerStatusService service) { this.service = service; }

    @GetMapping("/me")
    @Operation(summary = "Получить статус собственной заявки")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Статус заявки"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")})
    public PartnerApplicationOwnerStatusResponse myStatus(@AuthenticationPrincipal PartnerPrincipal owner) {
        var status = service.status(owner.provider(), owner.subject());
        return new PartnerApplicationOwnerStatusResponse(status.status(), status.latestDecisionReason());
    }
}
