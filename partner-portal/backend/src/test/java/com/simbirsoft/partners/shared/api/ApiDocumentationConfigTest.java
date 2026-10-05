package com.simbirsoft.partners.shared.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import com.simbirsoft.partners.access.api.PartnerApiKeyController;
import com.simbirsoft.partners.access.api.PartnerMcpAccessController;
import com.simbirsoft.partners.lead.api.PartnerLeadController;
import com.simbirsoft.partners.partner.api.PartnerProfileController;
import java.util.Arrays;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApiDocumentationConfigTest {
    private final ApiDocumentationConfig config = new ApiDocumentationConfig();

    @Test
    void partnerToolAllowlistIsExplicitAndExcludesKeyManagementRoutes() {
        var published = Arrays.stream(PartnerProfileController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class)).map(method -> method.getAnnotation(McpTool.class).name()).toList();
        assertEquals(java.util.Set.of("partner_profile_get", "partner_profile_update_settings"), java.util.Set.copyOf(published));
        var leadTools = Arrays.stream(PartnerLeadController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class)).map(method -> method.getAnnotation(McpTool.class).name()).toList();
        assertEquals(java.util.Set.of("partner_leads_board", "partner_lead_create", "partner_lead_create_with_attachment",
                "partner_lead_get", "partner_lead_attachment_download", "partner_lead_message"), java.util.Set.copyOf(leadTools));
        assertFalse(Arrays.stream(PartnerApiKeyController.class.getDeclaredMethods()).anyMatch(method -> method.isAnnotationPresent(McpTool.class)));
        assertFalse(Arrays.stream(PartnerMcpAccessController.class.getDeclaredMethods()).anyMatch(method -> method.isAnnotationPresent(McpTool.class)));
    }

    @Test
    void documentsV1ApiWithReusableErrorSchema() {
        OpenAPI api = config.partnerPortalOpenApi();
        assertEquals("v1", api.getInfo().getVersion());
        assertNotNull(api.getComponents().getSchemas().get("ApiError"));
        assertNotNull(api.getComponents().getSchemas().get("ApiError").getProperties().get("traceId"));
        assertNotNull(api.getComponents().getSchemas().get("ApiError").getProperties().get("fieldErrors"));
    }

    @Test
    void addsStandardErrorsOnlyToVersionedOperationsWithoutReplacingSpecificResponses() {
        StringSchema existingJsonSchema = new StringSchema();
        ApiResponse specificConflict = new ApiResponse().description("Specific conflict")
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new StringSchema()))
                        .addMediaType("application/json", new MediaType().schema(existingJsonSchema)));
        Operation versioned = new Operation().responses(new ApiResponses().addApiResponse("409", specificConflict));
        Operation unversioned = new Operation().responses(new ApiResponses());
        OpenAPI api = config.partnerPortalOpenApi().paths(new Paths()
                .addPathItem("/api/v1/example", new PathItem().get(versioned))
                .addPathItem("/status", new PathItem().get(unversioned)));

        config.apiErrorResponses().customise(api);

        for (String status : new String[]{"400", "401", "403", "404"}) {
            ApiResponse response = versioned.getResponses().get(status);
            assertNotNull(response);
            assertEquals("#/components/schemas/ApiError",
                    response.getContent().get("application/json").getSchema().get$ref());
            assertNotNull(response.getHeaders().get("X-Trace-Id"));
        }
        assertSame(specificConflict, versioned.getResponses().get("409"));
        assertEquals("Specific conflict", specificConflict.getDescription());
        assertNotNull(specificConflict.getContent().get("application/problem+json"));
        assertSame(existingJsonSchema,
                specificConflict.getContent().get("application/json").getSchema().getOneOf().get(0));
        assertEquals("#/components/schemas/ApiError",
                ((Schema<?>) specificConflict.getContent().get("application/json").getSchema().getOneOf().get(1)).get$ref());
        assertNotNull(specificConflict.getHeaders().get("X-Trace-Id"));
        assertFalse(unversioned.getResponses().containsKey("400"));
    }
}
