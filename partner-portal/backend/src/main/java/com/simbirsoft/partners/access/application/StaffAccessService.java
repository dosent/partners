package com.simbirsoft.partners.access.application;

import java.util.Locale;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccessService implements StaffRoleLookup {
    public static final String BOOTSTRAP_ADMIN = "ivan.neshin";
    private final StaffRoleGrantStore grants;

    public StaffAccessService(StaffRoleGrantStore grants) { this.grants = grants; }

    public Set<StaffRole> rolesFor(String username) { return grants.rolesFor(normalize(username)); }

    @Transactional(readOnly = true)
    public List<StaffRoleAssignment> usersWithRoles() { return grants.usersWithRoles(); }

    public Set<StaffPermission> permissionsFor(String username) {
        return StaffRolePolicy.permissions(rolesFor(username));
    }

    public boolean hasRole(String username, StaffRole role) { return rolesFor(username).contains(role); }

    public boolean hasPermission(String username, String permission) {
        return permissionsFor(username).stream().anyMatch(value -> value.value().equals(permission));
    }

    @Transactional
    public Set<StaffRole> replaceRoles(String username, Set<String> requested, String actor) {
        String normalizedUsername = normalize(username);
        Set<StaffRole> roles = requested.stream().map(value -> {
            try { return StaffRole.valueOf(value); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("Unknown staff role"); }
        }).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (BOOTSTRAP_ADMIN.equals(normalizedUsername) && !roles.contains(StaffRole.SYSTEM_ADMIN)) {
            throw new IllegalArgumentException("Bootstrap administrator role cannot be removed");
        }
        grants.replaceRoles(normalizedUsername, roles, normalize(actor));
        return roles;
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required");
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public record StaffRoleAssignment(String username, List<String> roles) { }
}
