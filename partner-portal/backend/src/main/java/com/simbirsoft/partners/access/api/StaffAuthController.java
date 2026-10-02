package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.access.application.StaffAccessService;
import com.simbirsoft.partners.access.application.StaffRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/staff")
public class StaffAuthController {
    private final StaffAuthFlow authFlow;
    private final StaffAccessService accessService;

    public StaffAuthController(StaffAuthFlow authFlow, StaffAccessService accessService) {
        this.authFlow = authFlow;
        this.accessService = accessService;
    }

    @PostMapping("/login")
    @Operation(summary = "Войти сотруднику через корпоративный LDAP")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия сотрудника создана"),
            @ApiResponse(responseCode = "401", description = "Неверные LDAP credentials"),
            @ApiResponse(responseCode = "503", description = "LDAP отключён или недоступен")
    })
    public StaffSessionResponse login(@Valid @RequestBody StaffLoginRequest login,
            HttpServletRequest request, HttpServletResponse response) {
        StaffIdentity identity = authFlow.login(login.username(), login.password(), request, response);
        return session(identity.username(), accessService.rolesFor(identity.username()));
    }

    @GetMapping("/session")
    @Operation(summary = "Получить текущую сессию сотрудника")
    public StaffSessionResponse session(@AuthenticationPrincipal StaffPrincipal principal) {
        return session(principal.username(), accessService.rolesFor(principal.username()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Завершить сессию сотрудника")
    public void logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private StaffSessionResponse session(String username, Set<StaffRole> roleSet) {
        List<String> roles = roleSet.stream().map(Enum::name).sorted().toList();
        List<String> permissions = accessService.permissionsFor(username).stream()
                .map(com.simbirsoft.partners.access.application.StaffPermission::value).sorted().toList();
        return new StaffSessionResponse(username, roles.isEmpty() ? "ACCESS_PENDING" : "ACTIVE", roles, permissions);
    }
}
