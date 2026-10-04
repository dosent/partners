package com.simbirsoft.partners.access.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class StaffRolePolicy {
    private static final Map<StaffRole, Set<StaffPermission>> MATRIX;
    static {
        EnumMap<StaffRole, Set<StaffPermission>> map = new EnumMap<>(StaffRole.class);
        map.put(StaffRole.PARTNER_MANAGER, EnumSet.of(StaffPermission.PARTNER_APPLICATION_READ,
                StaffPermission.PARTNER_APPLICATION_DECIDE, StaffPermission.LEAD_MANAGE));
        map.put(StaffRole.RESOURCE_MANAGER, EnumSet.of(StaffPermission.STAFFING_REQUEST_MANAGE,
                StaffPermission.CANDIDATE_MANAGE));
        map.put(StaffRole.FINANCE_CONTROLLER, EnumSet.of(StaffPermission.FINANCE_MANAGE));
        map.put(StaffRole.SYSTEM_ADMIN, EnumSet.of(StaffPermission.STAFF_ACCESS_MANAGE,
                StaffPermission.PARTNER_APPLICATION_READ, StaffPermission.PARTNER_APPLICATION_DECIDE,
                StaffPermission.LOCALIZATION_MANAGE, StaffPermission.DIRECTORY_MANAGE, StaffPermission.LEAD_MANAGE));
        MATRIX = Map.copyOf(map);
    }
    private StaffRolePolicy() {}
    public static Set<StaffPermission> permissions(Set<StaffRole> roles) {
        EnumSet<StaffPermission> result = EnumSet.noneOf(StaffPermission.class);
        roles.forEach(role -> result.addAll(MATRIX.get(role)));
        return Set.copyOf(result);
    }
}
