package com.simbirsoft.partners.access.api;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class PartnerAuthenticationToken extends AbstractAuthenticationToken {
    private final PartnerPrincipal principal;

    public PartnerAuthenticationToken(PartnerPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_PARTNER")));
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public PartnerPrincipal getPrincipal() {
        return principal;
    }
}
