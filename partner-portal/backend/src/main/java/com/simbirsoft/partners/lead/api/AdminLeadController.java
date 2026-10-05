package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.lead.application.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@Profile("!auth-test")
@RequestMapping("/api/v1/admin/leads")
@Tag(name="Lead management",description="Управление обработкой лидов сотрудниками")
public class AdminLeadController {
    private final LeadService service;
    public AdminLeadController(LeadService service) { this.service=service; }

    @GetMapping
    @Operation(summary="Просмотреть очередь лидов с фильтром и поиском")
    public AdminLeadPageResponse queue(@AuthenticationPrincipal StaffPrincipal actor,
            @RequestParam(required=false) com.simbirsoft.partners.lead.domain.LeadStatus status,
            @RequestParam(required=false) String query,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return AdminLeadPageResponse.from(service.staffPage(actor,status,query,page,size));
    }

    @GetMapping("/{leadId}")
    @Operation(summary="Получить рабочую карточку лида и все комментарии")
    public AdminLeadDetailResponse detail(@PathVariable UUID leadId,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return AdminLeadDetailResponse.from(service.staffDetail(actor,leadId));
    }

    @PostMapping("/{leadId}/status")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary="Изменить статус лида с проверкой допустимого перехода")
    public LeadCardResponse changeStatus(@PathVariable UUID leadId,
            @Valid @RequestBody ChangeLeadStatusRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return LeadCardResponse.from(service.changeStatus(leadId,request.status(),actor));
    }

    @PostMapping("/{leadId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Добавить внутренний или видимый партнёру комментарий")
    public AdminLeadMessageResponse message(@PathVariable UUID leadId,
            @Valid @RequestBody AdminLeadMessageRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return AdminLeadMessageResponse.from(service.sendStaffMessage(actor,leadId,request.body(),request.internal()));
    }
}
