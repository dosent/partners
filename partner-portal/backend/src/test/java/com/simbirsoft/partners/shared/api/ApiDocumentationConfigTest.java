package com.simbirsoft.partners.shared.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
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
    void documentsV1ApiWithReusableErrorSchema() {
        OpenAPI api = config.partnerPortalOpenApi();
        assertEquals("v1", api.getInfo().getVersion());
        assertNotNull(api.getComponents().getSchemas().get("ApiError"));
        assertNotNull(api.getComponents().getSchemas().get("ApiError").getProperties().get("traceId"));
        assertNotNull(api.getComponents().getSchemas().get("ApiError").getProperties().get("fieldErrors"));
    }

    @Test
    void addsStandardErrorsOnlyToVersionedOperationsWithoutReplacingSpecificResponses() {
        ApiResponse specificConflict = new ApiResponse().description("Specific conflict");
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
        assertFalse(unversioned.getResponses().containsKey("400"));
    }
}
