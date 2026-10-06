package com.simbirsoft.partners.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.simbirsoft.partners.mcp.backend.BackendApiClient;
import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog;
import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog.ToolCatalog;
import com.simbirsoft.partners.mcp.tools.OpenApiToolCallbackProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class McpServerApplicationContextTest {
    private static final MockWebServer backend = new MockWebServer();

    static {
        try { backend.start(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
        backend.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""
                        {"openapi":"3.0.3","paths":{"/api/v1/partners/me":{"get":{"x-mcp":{"enabled":true,"name":"partner_profile_get","description":"Read profile","behavior":"READ_ONLY"},"responses":{"200":{"description":"ok","content":{"application/json":{"schema":{"type":"object"}}}}}}}}}
                        """));
    }

    @AfterAll
    static void stopBackend() throws Exception { backend.shutdown(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("BACKEND_API_URL", () -> backend.url("/").toString());
        registry.add("BACKEND_OPENAPI_URL", () -> backend.url("/v3/api-docs").toString());
    }

    @Autowired BackendApiClient backendApiClient;
    @Autowired ToolCatalog catalog;
    @Autowired OpenApiToolCallbackProvider callbacks;

    @Test
    void applicationWiresRuntimeBackendClientAndOpenApiCatalog() {
        assertThat(backendApiClient).isNotNull();
        assertThat(catalog.tools()).containsKey("partner_profile_get");
        assertThat(callbacks.getToolCallbacks()).hasSize(1);
    }
}
