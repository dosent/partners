package com.simbirsoft.partners.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class McpToolOpenApiCustomizerTest {
    private final McpToolOpenApiCustomizer customizer = new McpToolOpenApiCustomizer();

    @SuppressWarnings("unchecked")
    @Test void markedHandlerExportsStableToolMetadata() throws Exception {
        Method method = Fixture.class.getMethod("marked");
        Operation operation = customizer.customize(new Operation(), new HandlerMethod(new Fixture(), method));
        assertThat(operation.getExtensions()).containsKey("x-mcp");
        var metadata = (java.util.Map<String, Object>) operation.getExtensions().get("x-mcp");
        assertThat(metadata.get("enabled")).isEqualTo(true);
        assertThat(metadata.get("name")).isEqualTo("partner_profile_get");
        assertThat(metadata.get("description")).isEqualTo("Read own profile");
        assertThat(metadata.get("behavior")).isEqualTo("READ_ONLY");
        var hints = (java.util.Map<String, Object>) metadata.get("hints");
        assertThat(hints).containsEntry("readOnlyHint", true).containsEntry("destructiveHint", false)
                .containsEntry("idempotentHint", true).containsEntry("openWorldHint", false);
    }

    @Test void unmarkedHandlerDoesNotExportToolMetadata() throws Exception {
        Method method = Fixture.class.getMethod("unmarked");
        Operation operation = customizer.customize(new Operation(), new HandlerMethod(new Fixture(), method));
        assertThat(operation.getExtensions()).isNull();
    }

    public static class Fixture {
        @McpTool(name="partner_profile_get", description="Read own profile", behavior=McpToolBehavior.READ_ONLY)
        public void marked() { }
        public void unmarked() { }
    }
}
