package com.simbirsoft.partners.access.application;

import java.util.Set;
import java.util.List;

public interface StaffRoleGrantStore {
    Set<StaffRole> rolesFor(String username);
    List<StaffAccessService.StaffRoleAssignment> usersWithRoles();
    void replaceRoles(String username, Set<StaffRole> roles, String actor);
}
