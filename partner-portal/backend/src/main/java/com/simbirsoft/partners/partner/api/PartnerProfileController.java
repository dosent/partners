package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.partner.application.PartnerProfileService;
import com.simbirsoft.partners.shared.api.McpTool;
import com.simbirsoft.partners.shared.api.McpToolBehavior;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partners")
@Tag(name = "Partner profile", description = "Профиль партнёра текущей сессии")
public class PartnerProfileController {
    private final PartnerProfileService service;

    public PartnerProfileController(PartnerProfileService service) { this.service = service; }

    @McpTool(name="partner_profile_get", description="Get the authenticated partner profile and settings", behavior=McpToolBehavior.READ_ONLY)
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Получить собственный профиль партнёра")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Профиль текущего партнёра"),
            @ApiResponse(responseCode = "401", description = "Требуется партнёрская сессия"),
            @ApiResponse(responseCode = "404", description = "Активный профиль не найден")})
    public PartnerProfileResponse current(@AuthenticationPrincipal PartnerPrincipal owner) {
        return PartnerProfileResponse.from(service.current(owner));
    }

    @McpTool(name="partner_profile_update_settings", description="Update the authenticated partner contact details and preferences", behavior=McpToolBehavior.MUTATING)
    @PutMapping(value = "/me/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Изменить контактные данные и настройки собственного профиля")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Профиль изменён"),
            @ApiResponse(responseCode = "400", description = "Некорректные настройки профиля"),
            @ApiResponse(responseCode = "401", description = "Требуется партнёрская сессия"),
            @ApiResponse(responseCode = "404", description = "Активный профиль не найден")})
    public PartnerProfileResponse update(@AuthenticationPrincipal PartnerPrincipal owner,
            @Valid @RequestBody PartnerProfileUpdateRequest request) {
        return PartnerProfileResponse.from(service.updateCurrent(owner, request.toCommand()));
    }
}
