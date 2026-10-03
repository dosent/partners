package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffRoleGrantStore;
import com.simbirsoft.partners.access.application.StaffAccessService.StaffRoleAssignment;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaStaffRoleGrantStore implements StaffRoleGrantStore {
    private final StaffRoleGrantRepository grants;
    private final StaffRoleAuditRepository audit;
    public JpaStaffRoleGrantStore(StaffRoleGrantRepository grants, StaffRoleAuditRepository audit) {
        this.grants = grants; this.audit = audit;
    }
    @Override @Transactional(readOnly = true)
    public Set<StaffRole> rolesFor(String username) {
        return grants.findAllByUsername(username).stream().map(StaffRoleGrantRecord::role)
                .collect(Collectors.toUnmodifiableSet());
    }
    @Override @Transactional(readOnly = true)
    public List<StaffRoleAssignment> usersWithRoles() {
        Map<String, List<String>> rolesByUsername = new java.util.TreeMap<>();
        grants.findAllByOrderByUsernameAscRoleAsc().forEach(grant -> {
            String username = grant.username().trim().toLowerCase(Locale.ROOT);
            rolesByUsername.computeIfAbsent(username, ignored -> new java.util.ArrayList<>())
                    .add(grant.role().name());
        });
        return rolesByUsername.entrySet().stream()
                .map(entry -> new StaffRoleAssignment(entry.getKey(), entry.getValue().stream().distinct().sorted().toList()))
                .toList();
    }
    @Override
    public void replaceRoles(String username, Set<StaffRole> desired, String actor) {
        Set<StaffRole> current = rolesFor(username);
        Set<StaffRole> removed = current.isEmpty() ? EnumSet.noneOf(StaffRole.class) : EnumSet.copyOf(current);
        removed.removeAll(desired);
        Set<StaffRole> added = desired.isEmpty() ? EnumSet.noneOf(StaffRole.class) : EnumSet.copyOf(desired);
        added.removeAll(current);
        grants.deleteAllByUsername(username);
        grants.flush();
        grants.saveAll(desired.stream().map(role -> new StaffRoleGrantRecord(username, role)).toList());
        audit.saveAll(java.util.stream.Stream.concat(
                added.stream().map(role -> new StaffRoleAuditRecord(username, role.name(), actor,
                        StaffRoleAuditRecord.Action.GRANTED)),
                removed.stream().map(role -> new StaffRoleAuditRecord(username, role.name(), actor,
                        StaffRoleAuditRecord.Action.REVOKED))).toList());
    }
}
