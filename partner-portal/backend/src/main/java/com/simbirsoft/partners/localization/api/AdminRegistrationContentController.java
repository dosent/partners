package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.localization.application.RegistrationContentService;
import com.simbirsoft.partners.localization.application.RegistrationContentService.ContentIncompleteException;
import com.simbirsoft.partners.shared.api.ApiError;
import com.simbirsoft.partners.shared.api.TraceIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/registration-content")
@Tag(name = "Registration content administration", description = "Черновики и публикация локалей")
public class AdminRegistrationContentController {
    private final RegistrationContentService service;

    public AdminRegistrationContentController(RegistrationContentService service) {
        this.service = service;
    }

    @GetMapping("/{locale}")
    @Operation(summary = "Получить черновик и опубликованную версию локали")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Состояние локали"),
            @ApiResponse(responseCode = "400", description = "Неизвестная локаль")})
    public RegistrationContentAdminResponse get(@Parameter @PathVariable String locale) {
        return RegistrationContentAdminResponse.from(service.getAdminState(locale));
    }

    @PutMapping("/{locale}/draft")
    @Operation(summary = "Сохранить черновик локали")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Черновик сохранён"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос или локаль"),
            @ApiResponse(responseCode = "409", description = "Версия черновика устарела")})
    public RegistrationContentAdminResponse saveDraft(@Parameter @PathVariable String locale,
            @Valid @RequestBody RegistrationContentDraftRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return RegistrationContentAdminResponse.from(
                service.saveDraft(locale, request.toCommand(), actor.username()));
    }

    @PostMapping("/{locale}/publish")
    @Operation(summary = "Опубликовать полную версию локали")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Версия опубликована"),
            @ApiResponse(responseCode = "400", description = "Некорректная локаль или запрос"),
            @ApiResponse(responseCode = "409", description = "Версия черновика устарела"),
            @ApiResponse(responseCode = "422", description = "Недостающие обязательные материалы")})
    public PublicRegistrationContentResponse publish(@Parameter @PathVariable String locale,
            @Valid @RequestBody PublishRegistrationContentRequest request,
            @AuthenticationPrincipal StaffPrincipal actor) {
        return PublicRegistrationContentResponse.from(
                service.publish(locale, request.expectedDraftVersion(), actor.username()));
    }

    @ExceptionHandler(ContentIncompleteException.class)
    ResponseEntity<ApiError> incomplete(ContentIncompleteException exception, HttpServletRequest request,
            HttpServletResponse response) {
        String traceId = response.getHeader(TraceIdFilter.HEADER);
        List<ApiError.FieldError> fields = exception.missingFields().stream()
                .map(field -> new ApiError.FieldError(field, "Required before publication"))
                .toList();
        return ResponseEntity.unprocessableEntity().body(new ApiError(Instant.now(), 422,
                "REGISTRATION_CONTENT_INCOMPLETE", "Required registration content is missing", traceId, fields));
    }
}
