package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffRoleGrantStore;
import java.util.EnumSet;
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
    @Override
    public void replaceRoles(String username, Set<StaffRole> desired, String actor) {
        Set<StaffRole> current = rolesFor(username);
        Set<StaffRole> removed = current.isEmpty() ? EnumSet.noneOf(StaffRole.class) : EnumSet.copyOf(current);
        removed.removeAll(desired);
        Set<StaffRole> added = desired.isEmpty() ? EnumSet.noneOf(StaffRole.class) : EnumSet.copyOf(desired);
        added.removeAll(current);
        grants.deleteAllByUsername(username);
        grants.saveAll(desired.stream().map(role -> new StaffRoleGrantRecord(username, role)).toList());
        audit.saveAll(java.util.stream.Stream.concat(
                added.stream().map(role -> new StaffRoleAuditRecord(username, role.name(), actor,
                        StaffRoleAuditRecord.Action.GRANTED)),
                removed.stream().map(role -> new StaffRoleAuditRecord(username, role.name(), actor,
                        StaffRoleAuditRecord.Action.REVOKED))).toList());
    }
}
