package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.partner.application.PartnerApplicationSubmissionService;
import com.simbirsoft.partners.partner.application.PartnerApplicationOwnerStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partner-applications")
@Tag(name = "Partner applications", description = "Статус собственной заявки партнёра")
public class PartnerApplicationOwnerController {
    private final PartnerApplicationOwnerStatusService service;
    private final PartnerApplicationSubmissionService submissionService;

    public PartnerApplicationOwnerController(PartnerApplicationOwnerStatusService service,
            PartnerApplicationSubmissionService submissionService) {
        this.service = service;
        this.submissionService = submissionService;
    }

    @GetMapping("/me")
    @Operation(summary = "Получить статус собственной заявки")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Статус заявки"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")})
    public PartnerApplicationOwnerStatusResponse myStatus(@AuthenticationPrincipal PartnerPrincipal owner) {
        var status = service.status(owner.provider(), owner.subject());
        return PartnerApplicationOwnerStatusResponse.from(status);
    }

    @PostMapping
    @Operation(summary = "Подать заявку на регистрацию партнёра")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Текущий статус заявки"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные или согласия"),
            @ApiResponse(responseCode = "409", description = "Состояние заявки изменилось или документы устарели"),
            @ApiResponse(responseCode = "503", description = "Для локали нет опубликованных документов")})
    public PartnerApplicationOwnerStatusResponse submit(@AuthenticationPrincipal PartnerPrincipal owner,
            @RequestBody @Valid PartnerApplicationSubmissionRequest request) {
        return PartnerApplicationOwnerStatusResponse.from(submissionService.submit(owner, request.toCommand()));
    }

    @PutMapping("/me")
    @Operation(summary = "Повторно подать исправленную заявку")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Заявка отправлена на ручную проверку"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные или согласия"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена"),
            @ApiResponse(responseCode = "409", description = "Текущий статус или документы не допускают повторную подачу"),
            @ApiResponse(responseCode = "503", description = "Для локали нет опубликованных документов")})
    public PartnerApplicationOwnerStatusResponse resubmit(@AuthenticationPrincipal PartnerPrincipal owner,
            @RequestBody @Valid PartnerApplicationSubmissionRequest request) {
        return PartnerApplicationOwnerStatusResponse.from(submissionService.resubmit(owner, request.toCommand()));
    }
}
