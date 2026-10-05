package com.simbirsoft.partners.mcp.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.simbirsoft.partners.mcp.catalog.McpToolDefinition;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class BackendApiClientTest {
    private MockWebServer server;
    private BackendApiClient client;

    @BeforeEach void start() throws Exception {
        server=new MockWebServer(); server.start();
        client=new BackendApiClient(WebClient.builder(),new ObjectMapper(),server.url("/").toString(),Duration.ofSeconds(2),1024);
    }
    @AfterEach void stop() throws Exception { server.shutdown(); }

    @Test void forwardsPartnerKeyAndUsesOnlyCatalogMethodAndPath() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type","application/json")
                .setHeader("X-Trace-Id","trace-123").setBody("{\"ok\":true}"));
        var result=client.invoke(tool(),Map.of("value","hello"),"secret-key");
        var request=server.takeRequest();
        assertThat(request.getHeader("X-Partner-API-Key")).isEqualTo("secret-key");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/v1/fixed");
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.traceId()).isEqualTo("trace-123");
    }

    @Test void rejectsCallerSuppliedUrlOrMethodArguments() {
        assertThatThrownBy(() -> client.invoke(tool(),Map.of("value","x","url","http://evil","method","DELETE"),"k"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void preservesBackend401And403Statuses() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"code\":\"UNAUTHORIZED\"}"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"code\":\"FORBIDDEN\"}"));
        assertThat(client.invoke(tool(),Map.of("value","x"),"k").status()).isEqualTo(401);
        assertThat(client.invoke(tool(),Map.of("value","x"),"k").status()).isEqualTo(403);
    }

    @Test void boundsBackendTimeoutAndResponseBytes() {
        server.enqueue(new MockResponse().setBodyDelay(250,java.util.concurrent.TimeUnit.MILLISECONDS).setBody("slow"));
        var shortTimeout=new BackendApiClient(WebClient.builder(),new ObjectMapper(),server.url("/").toString(),Duration.ofMillis(50),128);
        assertThat(shortTimeout.invoke(tool(),Map.of("value","x"),"k").status()).isEqualTo(504);
        server.enqueue(new MockResponse().setBody("x".repeat(2048)));
        assertThat(client.invoke(tool(),Map.of("value","x"),"k").status()).isEqualTo(502);
    }

    @Test void rejectsMultipartUploadsLargerThanTwentyMegabytes() {
        var multipart=new McpToolDefinition("partner_lead_create_with_attachment","Create lead","MUTATING","POST",
                "/api/v1/partner/leads",List.of(),JsonNodeFactory.instance.objectNode().set("properties",JsonNodeFactory.instance.objectNode()),
                JsonNodeFactory.instance.objectNode(),"multipart/form-data","application/json",true);
        byte[] tooLarge=new byte[BackendApiClient.MAX_UPLOAD_BYTES+1];
        assertThatThrownBy(() -> client.invokeMultipart(multipart,Map.of(),"k",Map.of(),"a.pdf","application/pdf",tooLarge))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20 MB");
    }

    private static McpToolDefinition tool() {
        var schema=JsonNodeFactory.instance.objectNode().put("type","object");
        schema.putObject("properties").putObject("value").put("type","string");
        return new McpToolDefinition("fixed_tool","Fixed operation","MUTATING","POST","/api/v1/fixed",List.of(),
                schema,schema,"application/json","application/json",false);
    }
}
