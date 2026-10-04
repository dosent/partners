package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.lead.application.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("!auth-test")
@RequestMapping("/api/v1/admin/leads")
@Tag(name="Lead management",description="Управление обработкой лидов сотрудниками")
public class AdminLeadController {
    private final LeadService service;
    public AdminLeadController(LeadService service) { this.service=service; }

    @PostMapping("/{leadId}/status")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary="Изменить статус лида с проверкой допустимого перехода")
    public LeadCardResponse changeStatus(@PathVariable UUID leadId,
            @Valid @RequestBody ChangeLeadStatusRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return LeadCardResponse.from(service.changeStatus(leadId,request.status(),actor));
    }
}
