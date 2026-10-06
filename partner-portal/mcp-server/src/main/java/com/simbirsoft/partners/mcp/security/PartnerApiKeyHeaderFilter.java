package com.simbirsoft.partners.mcp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.mcp.backend.BackendApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PartnerApiKeyHeaderFilter extends OncePerRequestFilter {
    public static final String HEADER=BackendApiClient.API_KEY_HEADER;
    public static final String REQUEST_ATTRIBUTE="partnerMcpApiKey";
    private final BackendApiClient backend;
    private final ObjectMapper mapper;
    private final long maxRequestBytes;

    public PartnerApiKeyHeaderFilter(BackendApiClient backend,ObjectMapper mapper,
            @Value("${mcp.request.max-bytes:31457280}") long maxRequestBytes) {
        this.backend=backend; this.mapper=mapper; this.maxRequestBytes=maxRequestBytes;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/mcp".equals(request.getServletPath());
    }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(request.getContentLengthLong()>maxRequestBytes) { fail(response,413,"REQUEST_TOO_LARGE","MCP request exceeds configured limit"); return; }
        String key=request.getHeader(HEADER);
        if(key==null||key.isBlank()) { fail(response,401,"UNAUTHORIZED","Partner API key required"); return; }
        int status=backend.verifyPartnerKey(key);
        if(status!=200) {
            if(status==401||status==403) fail(response,401,"UNAUTHORIZED","Invalid partner API key");
            else fail(response,503,"BACKEND_UNAVAILABLE","Partner authentication service unavailable");
            return;
        }
        request.setAttribute(REQUEST_ATTRIBUTE,key);
        byte[] payload=request.getInputStream().readNBytes(Math.toIntExact(maxRequestBytes)+1);
        if(payload.length>maxRequestBytes) { fail(response,413,"REQUEST_TOO_LARGE","MCP request exceeds configured limit"); return; }
        chain.doFilter(new ReplayableRequest(request,payload),response);
    }

    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] payload;
        ReplayableRequest(HttpServletRequest request,byte[] payload) { super(request); this.payload=payload; }
        @Override public int getContentLength() { return payload.length; }
        @Override public long getContentLengthLong() { return payload.length; }
        @Override public ServletInputStream getInputStream() {
            var input=new ByteArrayInputStream(payload);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available()==0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Async MCP input is disabled"); }
            };
        }
        @Override public BufferedReader getReader() {
            String encoding=getCharacterEncoding()==null?"UTF-8":getCharacterEncoding();
            try { return new BufferedReader(new InputStreamReader(getInputStream(),encoding)); }
            catch(java.io.UnsupportedEncodingException failure) { throw new IllegalStateException(failure); }
        }
    }

    private void fail(HttpServletResponse response,int status,String code,String message)throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setHeader("Cache-Control","no-store");
        mapper.writeValue(response.getOutputStream(),Map.of("status",status,"code",code,"message",message));
    }
}
