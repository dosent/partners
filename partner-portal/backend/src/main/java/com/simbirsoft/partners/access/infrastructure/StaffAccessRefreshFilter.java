package com.simbirsoft.partners.access.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.access.application.StaffAccessService;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.shared.api.ApiError;
import com.simbirsoft.partners.shared.api.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Refreshes app-owned authorities on every request and fences role-pending sessions. */
public final class StaffAccessRefreshFilter extends OncePerRequestFilter {
    private static final String SESSION = "/api/v1/auth/staff/session";
    private static final String LOGOUT = "/api/v1/auth/staff/logout";
    private final StaffAccessService access;
    private final ObjectMapper objectMapper;
    public StaffAccessRefreshFilter(StaffAccessService access, ObjectMapper objectMapper) {
        this.access = access; this.objectMapper = objectMapper;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && current.getPrincipal() instanceof StaffPrincipal principal) {
            Set<StaffRole> roles = access.rolesFor(principal.username());
            SecurityContextHolder.getContext().setAuthentication(new StaffAuthenticationToken(principal.username(), roles));
            if (roles.isEmpty() && !allowedPendingRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                String traceId = response.getHeader(TraceIdFilter.HEADER);
                if (traceId == null) traceId = UUID.randomUUID().toString();
                response.setHeader(TraceIdFilter.HEADER, traceId);
                response.setContentType("application/json");
                objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 403,
                        "ACCESS_PENDING", "Portal role assignment is required", traceId, List.of()));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allowedPendingRequest(HttpServletRequest request) {
        return ("GET".equals(request.getMethod()) && SESSION.equals(request.getRequestURI()))
                || ("POST".equals(request.getMethod()) && LOGOUT.equals(request.getRequestURI()));
    }
}
