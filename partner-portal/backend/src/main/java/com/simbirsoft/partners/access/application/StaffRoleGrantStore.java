package com.simbirsoft.partners.access.application;

import java.util.Set;

public interface StaffRoleGrantStore {
    Set<StaffRole> rolesFor(String username);
    void replaceRoles(String username, Set<StaffRole> roles, String actor);
}
