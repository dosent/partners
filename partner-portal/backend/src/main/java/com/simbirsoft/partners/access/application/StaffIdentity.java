package com.simbirsoft.partners.access.application;

import java.util.Set;

/** LDAP-confirmed identity plus the current app-owned RBAC grants (possibly empty). */
public record StaffIdentity(String username, Set<StaffRole> roles) {
    public StaffIdentity {
        roles = Set.copyOf(roles);
    }
}
