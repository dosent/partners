package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.StaffIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public final class StaffSessionService {
    private final SecurityContextRepository contextRepository;

    public StaffSessionService(SecurityContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    public void authenticate(StaffIdentity identity, HttpServletRequest request, HttpServletResponse response) {
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new StaffAuthenticationToken(identity));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
