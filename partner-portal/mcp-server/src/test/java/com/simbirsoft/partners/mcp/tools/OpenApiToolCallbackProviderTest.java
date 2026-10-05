package com.simbirsoft.partners.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.mcp.backend.BackendApiClient;
import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog;
import com.simbirsoft.partners.mcp.catalog.McpToolDefinition;
import java.time.Duration;
import java.util.Map;
import java.util.Base64;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.WebClient;

class OpenApiToolCallbackProviderTest {
    private MockWebServer server;
    private ObjectMapper mapper;
    private BackendApiClient backend;

    @BeforeEach void start() throws Exception {
        server=new MockWebServer(); server.start(); mapper=new ObjectMapper();
        backend=new BackendApiClient(WebClient.builder(),mapper,server.url("/").toString(),Duration.ofSeconds(2),1024*1024);
    }
    @AfterEach void stop() throws Exception { RequestContextHolder.resetRequestAttributes(); server.shutdown(); }

    @Test void exposesOnlyCatalogGenericToolsWithTheirOpenApiInputSchema() {
        var catalog=new OpenApiToolCatalog(mapper).parse("""
          {"openapi":"3.0.3","paths":{
           "/api/v1/profile":{"post":{"x-mcp":{"enabled":true,"name":"partner_profile_update","description":"Update own profile","behavior":"MUTATING"},
             "requestBody":{"content":{"application/json":{"schema":{"type":"object","properties":{"email":{"type":"string"}},"required":["email"]}}}}}},
           "/api/v1/file":{"post":{"x-mcp":{"enabled":true,"name":"partner_lead_create_with_attachment","description":"Create with file","behavior":"MUTATING"},
             "requestBody":{"content":{"multipart/form-data":{"schema":{"type":"object","properties":{"attachment":{"type":"string","format":"binary"}}}}}}}},
           "/api/v1/hidden":{"get":{"operationId":"not_exposed"}}
          }}
          """);
        var provider=new OpenApiToolCallbackProvider(catalog,backend,mapper);
        assertThat(provider.getToolCallbacks()).hasSize(1);
        var tool=provider.getToolCallbacks()[0].getToolDefinition();
        assertThat(tool.name()).isEqualTo("partner_profile_update");
        assertThat(tool.inputSchema()).contains("email").contains("required");
    }

    @Test void attachmentAdapterConvertsBase64InputToMultipartBackendRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setHeader("Content-Type","application/json").setBody("{\"id\":\"lead-1\"}"));
        var catalog=new OpenApiToolCatalog(mapper).parse("""
          {"openapi":"3.0.3","paths":{"/api/v1/partner/leads":{"post":{
           "x-mcp":{"enabled":true,"name":"partner_lead_create_with_attachment","description":"Create a lead with file","behavior":"MUTATING"},
           "requestBody":{"content":{"multipart/form-data":{"schema":{"type":"object","properties":{
             "request":{"type":"object","properties":{"company":{"type":"string"}},"required":["company"]},
             "attachment":{"type":"string","format":"binary"}}}}}}
          }}}}
          """);
        var request=new MockHttpServletRequest(); request.setServletPath("/mcp");
        request.setAttribute(com.simbirsoft.partners.mcp.security.PartnerApiKeyHeaderFilter.REQUEST_ATTRIBUTE,"partner-secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var callback=new PartnerLeadAttachmentToolAdapter(catalog,backend,mapper).getToolCallbacks()[0];
        String b64=Base64.getEncoder().encodeToString("synthetic-file".getBytes());
        String output=callback.call("{\"request\":{\"company\":\"Example\"},\"attachment\":{\"filename\":\"note.pdf\",\"contentType\":\"application/pdf\",\"base64\":\""+b64+"\"}}");
        var recorded=server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/partner/leads");
        assertThat(recorded.getHeader("X-Partner-API-Key")).isEqualTo("partner-secret");
        assertThat(recorded.getHeader("Content-Type")).startsWith("multipart/form-data;boundary=");
        assertThat(recorded.getBody().readUtf8()).contains("Example").contains("note.pdf").contains("synthetic-file");
        assertThat(output).contains("lead-1");
    }

    @Test void attachmentDownloadAdapterReturnsBoundedBinaryAsBase64() throws Exception {
        byte[] payload="synthetic-binary".getBytes();
        server.enqueue(new MockResponse().setHeader("Content-Type","application/octet-stream").setBody(new okio.Buffer().write(payload)));
        var catalog=new OpenApiToolCatalog(mapper).parse("""
          {"openapi":"3.0.3","paths":{"/api/v1/partner/leads/{leadId}/attachments/{attachmentId}":{"get":{
           "x-mcp":{"enabled":true,"name":"partner_lead_attachment_download","description":"Read a lead attachment","behavior":"READ_ONLY"},
           "parameters":[{"name":"leadId","in":"path","required":true,"schema":{"type":"string"}},
             {"name":"attachmentId","in":"path","required":true,"schema":{"type":"string"}}],
           "responses":{"200":{"content":{"application/octet-stream":{"schema":{"type":"string","format":"binary"}}}}}
          }}}}
          """);
        var request=new MockHttpServletRequest(); request.setServletPath("/mcp");
        request.setAttribute(com.simbirsoft.partners.mcp.security.PartnerApiKeyHeaderFilter.REQUEST_ATTRIBUTE,"partner-secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var callback=new PartnerLeadAttachmentToolAdapter(catalog,backend,mapper).getToolCallbacks()[0];
        String output=callback.call("{\"leadId\":\"l-1\",\"attachmentId\":\"a-2\"}");
        var recorded=server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/partner/leads/l-1/attachments/a-2");
        assertThat(output).contains(Base64.getEncoder().encodeToString(payload)).contains("application/octet-stream");
    }

    @Test void invokesOnlyTheCatalogOperationAndUsesCurrentRequestKey() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody("{\"updated\":true}"));
        var catalog=new OpenApiToolCatalog(mapper).parse("""
          {"openapi":"3.0.3","paths":{"/api/v1/profile":{"post":{
           "x-mcp":{"enabled":true,"name":"partner_profile_update","description":"Update own profile","behavior":"MUTATING"},
           "requestBody":{"content":{"application/json":{"schema":{"type":"object","properties":{"email":{"type":"string"}},"required":["email"]}}}}
          }}}}
          """);
        var request=new MockHttpServletRequest(); request.setServletPath("/mcp");
        request.setAttribute(com.simbirsoft.partners.mcp.security.PartnerApiKeyHeaderFilter.REQUEST_ATTRIBUTE,"partner-secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var callback=new OpenApiToolCallbackProvider(catalog,backend,mapper).getToolCallbacks()[0];
        var output=callback.call("{\"email\":\"partner@example.test\"}");
        var recorded=server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/v1/profile");
        assertThat(recorded.getHeader("X-Partner-API-Key")).isEqualTo("partner-secret");
        assertThat(recorded.getBody().readUtf8()).contains("partner@example.test");
        assertThat(output).contains("updated").contains("200");
    }
}
