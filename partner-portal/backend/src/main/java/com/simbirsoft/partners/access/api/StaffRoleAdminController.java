package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.StaffAccessService;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.access.application.StaffRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/staff-access/users")
@Tag(name = "Staff access administration", description = "Назначение ролей сотрудникам портала")
public class StaffRoleAdminController {
    private final StaffAccessService access;
    public StaffRoleAdminController(StaffAccessService access) { this.access = access; }

    @GetMapping
    @Operation(summary = "Получить список сотрудников с назначенными ролями")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Список назначений ролей"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    public UsersResponse users(@AuthenticationPrincipal StaffPrincipal actor) {
        requireAdministrator(actor);
        return new UsersResponse(access.usersWithRoles().stream()
                .map(user -> new RoleResponse(user.username(), user.roles())).toList());
    }

    @GetMapping("/{username}/roles")
    @Operation(summary = "Получить назначенные сотруднику роли")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Назначенные роли"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    public RoleResponse roles(@Parameter(description = "Логин сотрудника") @PathVariable String username,
            @AuthenticationPrincipal StaffPrincipal actor) {
        requireAdministrator(actor);
        return response(username, access.rolesFor(username));
    }

    @PutMapping("/{username}/roles")
    @Operation(summary = "Заменить набор ролей сотрудника")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Назначения обновлены"),
            @ApiResponse(responseCode = "400", description = "Неизвестная роль или запрещённое изменение"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    public RoleResponse replace(@Parameter(description = "Логин сотрудника") @PathVariable String username,
            @Valid @RequestBody ReplaceRolesRequest body,
            @AuthenticationPrincipal StaffPrincipal actor) {
        requireAdministrator(actor);
        try {
            return response(username, access.replaceRoles(username, Set.copyOf(body.roles()), actor.username()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private void requireAdministrator(StaffPrincipal actor) {
        if (actor == null || !access.hasPermission(actor.username(), "staff.access.manage")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission");
        }
    }
    private static RoleResponse response(String username, Set<StaffRole> roles) {
        return new RoleResponse(username, roles.stream().map(Enum::name).sorted().toList());
    }
    @Schema(description = "Набор ролей портала для назначения сотруднику")
    public record ReplaceRolesRequest(@NotNull Set<@NotBlank String> roles) {}
    @Schema(description = "Назначенные роли сотрудника")
    public record RoleResponse(String username, List<String> roles) {}
    @Schema(description = "Пользователи портала с назначенными ролями")
    public record UsersResponse(List<RoleResponse> users) {}
}
