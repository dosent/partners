package com.simbirsoft.partners.partner.api;

import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.partner.application.PartnerApplicationModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/partner-applications")
@Tag(name = "Partner application moderation", description = "Очередь и решения по заявкам партнёров")
public class PartnerApplicationModerationController {
    private final PartnerApplicationModerationService service;

    public PartnerApplicationModerationController(PartnerApplicationModerationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Получить очередь ручной проверки")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Страница очереди"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    public ManualReviewQueueResponse queue(
            @Parameter(description = "Номер страницы, начиная с нуля") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Размер страницы от 1 до 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.manualReviewPage(page, size);
        return new ManualReviewQueueResponse(result.items().stream()
                .map(item -> new ManualReviewQueueResponse.Item(item.id(), item.submittedAt(), item.revisionNumber())).toList(),
                result.page(), result.size(), result.total());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить карточку заявки")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Карточка заявки"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена")})
    public PartnerApplicationDetailResponse detail(@PathVariable UUID id) {
        return PartnerApplicationDetailResponse.from(service.detail(id));
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Одобрить заявку")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Заявка одобрена"),
            @ApiResponse(responseCode = "400", description = "Некорректное назначение менеджера"),
            @ApiResponse(responseCode = "409", description = "Заявка уже решена")})
    public PartnerApplicationDetailResponse approve(@PathVariable UUID id,
            @RequestBody(required = false) @Valid ApprovePartnerApplicationRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return PartnerApplicationDetailResponse.from(service.approve(id, actor.username(),
                request == null ? null : request.assignedManagerUsername()));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Отклонить заявку")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Заявка отклонена"),
            @ApiResponse(responseCode = "400", description = "Причина недопустима"),
            @ApiResponse(responseCode = "409", description = "Заявка уже решена")})
    public PartnerApplicationDetailResponse reject(@PathVariable UUID id,
            @RequestBody @Valid ModerationDecisionRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return PartnerApplicationDetailResponse.from(service.reject(id, actor.username(), request.reason()));
    }

    @PostMapping("/{id}/request-changes")
    @Operation(summary = "Запросить изменения в заявке")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Изменения запрошены"),
            @ApiResponse(responseCode = "400", description = "Причина недопустима"),
            @ApiResponse(responseCode = "409", description = "Заявка уже решена")})
    public PartnerApplicationDetailResponse requestChanges(@PathVariable UUID id,
            @RequestBody @Valid ModerationDecisionRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return PartnerApplicationDetailResponse.from(service.requestChanges(id, actor.username(), request.reason()));
    }
}
