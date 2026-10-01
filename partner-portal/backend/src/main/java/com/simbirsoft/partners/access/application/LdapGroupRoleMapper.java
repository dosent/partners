package com.simbirsoft.partners.access.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LdapGroupRoleMapper {
    private final Map<StaffRole, String> groupByRole;

    public LdapGroupRoleMapper(Map<String, String> roleGroupMappings) {
        this.groupByRole = new EnumMap<>(StaffRole.class);
        roleGroupMappings.forEach((roleName, groupDn) -> {
            if (groupDn != null && !groupDn.isBlank()) {
                groupByRole.put(StaffRole.valueOf(roleName), normalize(groupDn));
            }
        });
    }

    public Set<StaffRole> map(Set<String> directoryGroups) {
        Set<String> normalizedGroups = new HashSet<>();
        directoryGroups.forEach(group -> normalizedGroups.add(normalize(group)));
        EnumSet<StaffRole> roles = EnumSet.noneOf(StaffRole.class);
        groupByRole.forEach((role, groupDn) -> {
            if (normalizedGroups.contains(groupDn)) {
                roles.add(role);
            }
        });
        if (roles.isEmpty()) {
            throw new StaffAccessDeniedException();
        }
        return Set.copyOf(roles);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
