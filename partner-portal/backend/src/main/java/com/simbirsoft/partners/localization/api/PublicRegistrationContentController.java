package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.localization.application.PublishedRegistrationContentLookup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public/registration-content")
@Tag(name = "Public registration content", description = "Опубликованные тексты регистрации")
public class PublicRegistrationContentController {
    private final PublishedRegistrationContentLookup content;

    public PublicRegistrationContentController(PublishedRegistrationContentLookup content) {
        this.content = content;
    }

    @GetMapping("/{locale}")
    @Operation(summary = "Получить опубликованные тексты регистрации")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Опубликованный набор локали"),
            @ApiResponse(responseCode = "503", description = "Локаль пока не опубликована")})
    public PublicRegistrationContentResponse get(
            @Parameter(description = "Локаль RU, KZ или EN; неизвестная локаль использует RU")
            @PathVariable String locale) {
        return content.findPublished(locale).map(PublicRegistrationContentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Registration content is not published"));
    }
}
