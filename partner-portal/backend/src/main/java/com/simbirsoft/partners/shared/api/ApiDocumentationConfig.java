package com.simbirsoft.partners.shared.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.headers.Header;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocumentationConfig {
    @Bean
    OpenAPI partnerPortalOpenApi() {
        Components components = new Components();
        ModelConverters.getInstance().readAll(ApiError.class).forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info().title("Partner Portal API").version("v1")
                        .description("Версионированный JSON API партнёрского портала"))
                .components(components);
    }

    @Bean
    OpenApiCustomizer apiErrorResponses() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (!path.startsWith("/api/v1/")) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> {
                    ApiResponses responses = operation.getResponses();
                    if (responses == null) {
                        responses = new ApiResponses();
                        operation.setResponses(responses);
                    }
                    addResponse(responses, "400", "Некорректный запрос");
                    addResponse(responses, "401", "Требуется аутентификация");
                    addResponse(responses, "403", "Доступ запрещён");
                    addResponse(responses, "404", "Ресурс не найден");
                    addResponse(responses, "409", "Конфликт состояния");
                });
            });
        };
    }

    private void addResponse(ApiResponses responses, String status, String description) {
        if (responses.containsKey(status)) {
            return;
        }
        ApiResponse response = new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))))
                .addHeaderObject(TraceIdFilter.HEADER,
                        new Header().description("Идентификатор запроса").schema(new StringSchema()));
        responses.addApiResponse(status, response);
    }
}
