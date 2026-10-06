package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class PartnerApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private final PartnerPrincipal principal;
    private final UUID keyId;
    public PartnerApiKeyAuthenticationToken(UUID keyId, PartnerPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_PARTNER"), new SimpleGrantedAuthority("PARTNER_API_KEY")));
        this.keyId=keyId; this.principal=principal; super.setAuthenticated(true);
    }
    @Override public Object getCredentials() { return null; }
    @Override public PartnerPrincipal getPrincipal() { return principal; }
    public UUID keyId() { return keyId; }
}
