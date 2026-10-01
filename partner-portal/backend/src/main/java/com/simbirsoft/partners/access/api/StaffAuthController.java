package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
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

    public StaffAuthController(StaffAuthFlow authFlow) {
        this.authFlow = authFlow;
    }

    @PostMapping("/login")
    @Operation(summary = "Войти сотруднику через корпоративный LDAP")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия сотрудника создана"),
            @ApiResponse(responseCode = "401", description = "Неверные LDAP credentials"),
            @ApiResponse(responseCode = "403", description = "LDAP-группы не дают роль портала"),
            @ApiResponse(responseCode = "503", description = "LDAP отключён или недоступен")
    })
    public StaffSessionResponse login(@Valid @RequestBody StaffLoginRequest login,
            HttpServletRequest request, HttpServletResponse response) {
        StaffIdentity identity = authFlow.login(login.username(), login.password(), request, response);
        return session(identity.username(), identity.roles().stream().map(Enum::name).sorted().toList());
    }

    @GetMapping("/session")
    @Operation(summary = "Получить текущую сессию сотрудника")
    public StaffSessionResponse session(@AuthenticationPrincipal StaffPrincipal principal) {
        List<String> roles = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities().stream()
                .map(authority -> authority.getAuthority().substring("ROLE_".length()))
                .sorted().toList();
        return session(principal.username(), roles);
    }

    private StaffSessionResponse session(String username, List<String> roles) {
        return new StaffSessionResponse(username, roles);
    }
}
