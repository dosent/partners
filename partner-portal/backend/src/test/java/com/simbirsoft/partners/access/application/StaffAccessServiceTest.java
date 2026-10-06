package com.simbirsoft.partners.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class StaffAccessServiceTest {
    private final StaffRoleGrantStore grants = mock(StaffRoleGrantStore.class);
    private final StaffAccessService service = new StaffAccessService(grants);

    @Test
    void usernamesWithRoleReturnsSortedLowercaseUsernamesForRequestedRole() {
        when(grants.usernamesWithRole(StaffRole.RESOURCE_MANAGER))
                .thenReturn(List.of("zoe.smith", "ALICE.JONES", "mike.lee"));

        assertThat(service.usernamesWithRole(StaffRole.RESOURCE_MANAGER))
                .containsExactly("alice.jones", "mike.lee", "zoe.smith");
    }

    @Test
    void usernamesWithRoleReturnsEmptyListWhenRoleHasNoGrants() {
        when(grants.usernamesWithRole(StaffRole.RESOURCE_MANAGER)).thenReturn(List.of());

        assertThat(service.usernamesWithRole(StaffRole.RESOURCE_MANAGER)).isEmpty();
    }

    @Test
    void usernamesWithRoleQueriesOnlyTheRequestedRole() {
        when(grants.usernamesWithRole(StaffRole.RESOURCE_MANAGER)).thenReturn(List.of("resource.manager"));

        assertThat(service.usernamesWithRole(StaffRole.RESOURCE_MANAGER)).containsExactly("resource.manager");
        org.mockito.Mockito.verify(grants).usernamesWithRole(StaffRole.RESOURCE_MANAGER);
    }
}
