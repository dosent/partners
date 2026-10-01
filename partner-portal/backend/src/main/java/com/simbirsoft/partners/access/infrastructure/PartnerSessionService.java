package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.ExternalIdentity;
import com.simbirsoft.partners.access.application.PartnerPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public final class PartnerSessionService {
    private final SecurityContextRepository contextRepository;

    public PartnerSessionService(SecurityContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    public PartnerPrincipal authenticate(ExternalIdentity identity, HttpServletRequest request,
            HttpServletResponse response) {
        PartnerPrincipal principal = new PartnerPrincipal(identity.provider(), identity.subject());
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PartnerAuthenticationToken(principal));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return principal;
    }
}
