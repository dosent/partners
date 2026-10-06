package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.staffing.application.StaffingRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!auth-test")
@RequestMapping("/api/v1/admin/staffing-requests")
@Tag(name="Staffing request management", description="Назначение ответственных на заявки специалистов")
@ApiResponses({@ApiResponse(responseCode="401", description="Требуется сессия сотрудника"),
        @ApiResponse(responseCode="403", description="Нет разрешения staffing.request.manage")})
public class StaffingRequestAdminController {
    private final StaffingRequestService service;

    public StaffingRequestAdminController(StaffingRequestService service) { this.service=service; }

    @GetMapping
    @Operation(summary="Получить очередь заявок для назначения ответственного")
    public StaffingRequestQueuePageResponse queue(@AuthenticationPrincipal StaffPrincipal actor,
            @Parameter(description="Номер страницы, начиная с нуля") @RequestParam(defaultValue="0") @Min(0) int page,
            @Parameter(description="Количество заявок на странице") @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return StaffingRequestQueuePageResponse.from(service.staffPage(actor, page, size));
    }

    @GetMapping("/assignees")
    @Operation(summary="Получить сотрудников с ролью RESOURCE_MANAGER")
    public List<StaffingRequestAssigneeResponse> assignees(@AuthenticationPrincipal StaffPrincipal actor) {
        return service.assignees(actor).stream().map(StaffingRequestAssigneeResponse::from).toList();
    }

    @PutMapping("/{requestId}/assignment")
    @Operation(summary="Назначить, заменить или снять ответственного")
    @ApiResponses({@ApiResponse(responseCode="200", description="Текущее назначение и версия заявки"),
            @ApiResponse(responseCode="400", description="Некорректный username или версия"),
            @ApiResponse(responseCode="404", description="Заявка не найдена"),
            @ApiResponse(responseCode="409", description="Заявка изменена другим запросом")})
    public StaffingRequestQueuePageResponse.Item assign(@AuthenticationPrincipal StaffPrincipal actor,
            @Parameter(description="ID заявки") @PathVariable UUID requestId,
            @Valid @RequestBody StaffingRequestAssignmentRequest request) {
        return StaffingRequestQueuePageResponse.Item.from(service.assignResourceManager(actor, requestId,
                request.username(), request.expectedVersion()));
    }
}
