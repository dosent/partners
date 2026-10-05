package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/yandex")
@Tag(name = "Аутентификация партнёра", description = "Вход партнёров через Yandex ID")
public class YandexAuthController {
    private final YandexAuthFlow authFlow;

    public YandexAuthController(YandexAuthFlow authFlow) {
        this.authFlow = authFlow;
    }

    @GetMapping("/start")
    @Operation(summary = "Начать вход через Yandex ID")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Перенаправление в Yandex ID"),
            @ApiResponse(responseCode = "503", description = "OAuth-приложение не настроено")
    })
    public ResponseEntity<Void> start(HttpServletRequest request) {
        URI location = authFlow.start(request);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/callback")
    @Operation(summary = "Завершить вход через Yandex ID")
    @ApiResponses({
            @ApiResponse(responseCode = "303", description = "Сессия создана"),
            @ApiResponse(responseCode = "401", description = "OAuth state отсутствует, истёк или не совпадает"),
            @ApiResponse(responseCode = "502", description = "Yandex ID недоступен")
    })
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {
        authFlow.callback(code, state, error, request, response);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/")).build();
    }

    @GetMapping("/session")
    @Operation(summary = "Получить текущую сессию партнёра")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Идентичность текущего партнёра"),
            @ApiResponse(responseCode = "401", description = "Требуется вход")
    })
    public PartnerSessionResponse session(@AuthenticationPrincipal PartnerPrincipal principal) {
        return new PartnerSessionResponse(principal.provider(), principal.subject());
    }

    @PostMapping("/logout")
    @Operation(summary = "Завершить сессию партнёра")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Сессия партнёра завершена"),
            @ApiResponse(responseCode = "401", description = "Требуется вход"),
            @ApiResponse(responseCode = "403", description = "Отсутствует действительный CSRF-токен")
    })
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
