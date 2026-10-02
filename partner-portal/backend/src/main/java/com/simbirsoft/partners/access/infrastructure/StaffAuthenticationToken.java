package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.access.application.StaffPermission;
import com.simbirsoft.partners.access.application.StaffRolePolicy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class StaffAuthenticationToken extends AbstractAuthenticationToken {
    private final StaffPrincipal principal;

    public StaffAuthenticationToken(StaffIdentity identity) {
        this(identity.username(), identity.roles());
    }

    public StaffAuthenticationToken(String username, java.util.Set<com.simbirsoft.partners.access.application.StaffRole> roles) {
        super(authorities(roles));
        this.principal = new StaffPrincipal(username);
        super.setAuthenticated(true);
    }

    private static java.util.List<SimpleGrantedAuthority> authorities(
            java.util.Set<com.simbirsoft.partners.access.application.StaffRole> roles) {
        ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
        StaffRolePolicy.permissions(roles).stream().map(StaffPermission::value)
                .map(SimpleGrantedAuthority::new).forEach(authorities::add);
        return authorities;
    }

    @Override public Object getCredentials() { return null; }
    @Override public StaffPrincipal getPrincipal() { return principal; }
}
