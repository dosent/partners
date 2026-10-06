package com.simbirsoft.partners.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.mcp.backend.BackendApiClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.reactive.function.client.WebClient;

class PartnerApiKeyHeaderFilterTest {
    private MockWebServer backend;
    @BeforeEach void start() throws Exception { backend=new MockWebServer(); backend.start(); }
    @AfterEach void stop() throws Exception { backend.shutdown(); }

    @Test void rejectsMissingKeyBeforeMcpTransport() throws Exception {
        var filter=filter(1024); var request=request("/mcp","hello",null); var response=new MockHttpServletResponse();
        var called=new AtomicReference<>(false);
        filter.doFilter(request,response,(req,res)->called.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(called.get()).isFalse();
        assertThat(backend.getRequestCount()).isZero();
    }

    @Test void verifiesKeyForEveryMcpRequestAndReplaysBoundedBody() throws Exception {
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("{\"active\":true}"));
        var filter=filter(1024); var request=request("/mcp","hello","secret-key"); var response=new MockHttpServletResponse();
        var forwardedBody=new AtomicReference<String>();
        filter.doFilter(request,response,(req,res)->forwardedBody.set(new String(req.getInputStream().readAllBytes())));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(forwardedBody.get()).isEqualTo("hello");
        assertThat(request.getAttribute(PartnerApiKeyHeaderFilter.REQUEST_ATTRIBUTE)).isEqualTo("secret-key");
        assertThat(backend.takeRequest().getHeader("X-Partner-API-Key")).isEqualTo("secret-key");
    }

    @Test void rejectsChunkedBodyBeyondConfiguredLimit() throws Exception {
        backend.enqueue(new MockResponse().setResponseCode(200));
        var filter=filter(4); var request=request("/mcp","12345","secret-key"); var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->{});
        assertThat(response.getStatus()).isEqualTo(413);
    }

    private PartnerApiKeyHeaderFilter filter(long max) {
        var client=new BackendApiClient(WebClient.builder(),new ObjectMapper(),backend.url("/").toString(),Duration.ofSeconds(1),1024);
        return new PartnerApiKeyHeaderFilter(client,new ObjectMapper(),max);
    }
    private static MockHttpServletRequest request(String path,String body,String key) {
        var request=new MockHttpServletRequest("POST",path); request.setServletPath(path); request.setContent(body.getBytes());
        if(key!=null)request.addHeader("X-Partner-API-Key",key); return request;
    }
}
