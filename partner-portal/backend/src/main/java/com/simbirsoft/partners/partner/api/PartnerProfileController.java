package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.partner.application.PartnerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    @GetMapping("/me")
    @Operation(summary = "Получить собственный профиль партнёра")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Профиль текущего партнёра"),
            @ApiResponse(responseCode = "401", description = "Требуется партнёрская сессия"),
            @ApiResponse(responseCode = "404", description = "Активный профиль не найден")})
    public PartnerProfileResponse current(@AuthenticationPrincipal PartnerPrincipal owner) {
        return PartnerProfileResponse.from(service.current(owner));
    }

    @PutMapping("/me/settings")
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
