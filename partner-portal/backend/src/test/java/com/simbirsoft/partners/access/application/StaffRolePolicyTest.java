package com.simbirsoft.partners.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaffRolePolicyTest {
    @Test
    void systemAdministratorCanManageLeadStatus() {
        assertThat(StaffRolePolicy.permissions(Set.of(StaffRole.SYSTEM_ADMIN)))
                .contains(StaffPermission.LEAD_MANAGE);
    }

    @Test
    void unrelatedBusinessRolesDoNotGainLeadManagement() {
        assertThat(StaffRolePolicy.permissions(Set.of(StaffRole.RESOURCE_MANAGER)))
                .doesNotContain(StaffPermission.LEAD_MANAGE);
    }
}
