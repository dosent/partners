package com.simbirsoft.partners.access.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.access.application.PartnerApiKeyService;
import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.shared.api.ApiError;
import com.simbirsoft.partners.shared.api.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class PartnerApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Partner-API-Key";
    private final PartnerApiKeyService service;
    private final ObjectMapper objectMapper;
    public PartnerApiKeyAuthenticationFilter(PartnerApiKeyService service, ObjectMapper objectMapper) {
        this.service=service; this.objectMapper=objectMapper;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null) {
            var identity = service.authenticate(key);
            if (identity.isEmpty()) {
                writeUnauthorized(request, response);
                return;
            }
            var value=identity.get();
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new PartnerApiKeyAuthenticationToken(value.id(),
                    new PartnerPrincipal(value.provider(), value.subject())));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String traceId = response.getHeader(TraceIdFilter.HEADER);
        if (traceId == null) traceId=UUID.randomUUID().toString();
        response.setStatus(401); response.setContentType("application/json"); response.setHeader(TraceIdFilter.HEADER, traceId);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 401, "UNAUTHORIZED",
                "Invalid partner API key", traceId, List.of()));
    }
}
