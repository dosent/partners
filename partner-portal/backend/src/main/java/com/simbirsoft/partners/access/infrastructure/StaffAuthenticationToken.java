package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class StaffAuthenticationToken extends AbstractAuthenticationToken {
    private final StaffPrincipal principal;

    public StaffAuthenticationToken(StaffIdentity identity) {
        super(identity.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList());
        this.principal = new StaffPrincipal(identity.username());
        super.setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public StaffPrincipal getPrincipal() { return principal; }
}
