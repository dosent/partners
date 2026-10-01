package com.simbirsoft.partners.access.application;

import java.util.Set;

public record StaffIdentity(String username, Set<StaffRole> roles) {
    public StaffIdentity {
        roles = Set.copyOf(roles);
        if (roles.isEmpty()) {
            throw new StaffAccessDeniedException();
        }
    }
}
