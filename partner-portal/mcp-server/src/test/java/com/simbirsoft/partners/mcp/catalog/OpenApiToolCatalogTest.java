package com.simbirsoft.partners.mcp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenApiToolCatalogTest {
    private final OpenApiToolCatalog catalog = new OpenApiToolCatalog(new ObjectMapper());

    @Test void extractsOnlyExplicitlyEnabledToolWithSchema() {
        var loaded = catalog.parse(spec("""
          "x-mcp":{"enabled":true,"name":"partner_profile_get","description":"Read profile","behavior":"READ_ONLY"},
          "parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"string"}}]
        """));
        assertThat(loaded.tools()).containsKey("partner_profile_get");
        assertThat(loaded.tools()).doesNotContainKey("hidden_operation");
        assertThat(loaded.tools().get("partner_profile_get").method()).isEqualTo("GET");
        assertThat(loaded.tools().get("partner_profile_get").path()).isEqualTo("/api/v1/partner/leads/{id}");
    }

    @Test void rejectsDuplicateToolNames() {
        String metadata = "\"x-mcp\":{\"enabled\":true,\"name\":\"same_tool\",\"description\":\"Read\",\"behavior\":\"READ_ONLY\"}";
        String duplicate = "{\"openapi\":\"3.0.3\",\"paths\":{"+
                "\"/api/v1/one\":{\"get\":{"+metadata+"}},"+
                "\"/api/v1/two\":{\"get\":{"+metadata+"}}}}";
        assertThatThrownBy(() -> catalog.parse(duplicate)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate MCP tool name");
    }

    @Test void rejectsMalformedEnabledExtension() {
        assertThatThrownBy(() -> catalog.parse(spec("\"x-mcp\":{\"enabled\":true,\"name\":\"\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnsupportedRequestAndResponseMediaTypes() {
        assertThatThrownBy(() -> catalog.parse(spec("""
          "x-mcp":{"enabled":true,"name":"xml_tool","description":"xml","behavior":"READ_ONLY"},
          "requestBody":{"content":{"application/xml":{"schema":{"type":"object"}}}}
        """))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnsupportedCompositionKeywordsAtCatalogLoad() {
        String json = """
          {"openapi":"3.0.3","paths":{"/api/v1/tool":{"post":{
            "x-mcp":{"enabled":true,"name":"composed_tool","description":"tool","behavior":"MUTATING"},
            "requestBody":{"content":{"application/json":{"schema":{"type":"object","properties":{
              "value":{"oneOf":[{"type":"string"},{"type":"integer"}]}
            }}}}}
          }}}}
          """;
        assertThatThrownBy(() -> catalog.parse(json)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported MCP schema keyword");
    }


    @Test void rejectsUnsupportedResponseMediaType() {
        String json = """
          {"openapi":"3.0.3","paths":{"/api/v1/xml":{"get":{
            "x-mcp":{"enabled":true,"name":"xml_tool","description":"xml","behavior":"READ_ONLY"},
            "responses":{"200":{"content":{"application/xml":{"schema":{"type":"string"}}}}}
          }}}}
          """;
        assertThatThrownBy(() -> catalog.parse(json)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String spec(String operationFields) {
        return """
          {"openapi":"3.0.3","paths":{
            "/api/v1/partner/leads/{id}":{"get":{"operationId":"visible",%s}},
            "/api/v1/partner/hidden":{"get":{"operationId":"hidden_operation"}}
          }}
          """.formatted(operationFields);
    }
}
