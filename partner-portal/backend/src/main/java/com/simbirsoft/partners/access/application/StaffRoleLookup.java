package com.simbirsoft.partners.access.application;

import java.util.Set;

/** Cross-module read-only contract for checking app-owned staff roles and permissions. */
public interface StaffRoleLookup {
    Set<StaffRole> rolesFor(String username);
    Set<StaffPermission> permissionsFor(String username);
    boolean hasRole(String username, StaffRole role);
    boolean hasPermission(String username, String permission);
}
