package com.simbirsoft.partners.access.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LdapGroupRoleMapperTest {
    @Test
    void mapsConfiguredDirectoryGroupsToInternalRoles() {
        var mapper = new LdapGroupRoleMapper(Map.of(
                "PARTNER_MANAGER", "CN=Partner Managers,OU=Portal,DC=example,DC=com",
                "FINANCE_CONTROLLER", "CN=Finance,OU=Portal,DC=example,DC=com"));

        Set<StaffRole> roles = mapper.map(Set.of(
                "CN=Partner Managers,OU=Portal,DC=example,DC=com",
                "CN=Unknown,OU=Other,DC=example,DC=com"));

        assertEquals(Set.of(StaffRole.PARTNER_MANAGER), roles);
    }

    @Test
    void rejectsAuthenticatedUserWithoutAnyConfiguredRoleGroup() {
        var mapper = new LdapGroupRoleMapper(Map.of(
                "PARTNER_MANAGER", "CN=Partner Managers,OU=Portal,DC=example,DC=com"));

        assertThrows(StaffAccessDeniedException.class,
                () -> mapper.map(Set.of("CN=Everyone,OU=Portal,DC=example,DC=com")));
    }

    @Test
    void groupDistinguishedNamesAreComparedWithoutCaseSensitivity() {
        var mapper = new LdapGroupRoleMapper(Map.of(
                "SYSTEM_ADMIN", "CN=Portal Admins,OU=Portal,DC=example,DC=com"));

        assertEquals(Set.of(StaffRole.SYSTEM_ADMIN), mapper.map(Set.of(
                "cn=portal admins,ou=portal,dc=example,dc=com")));
    }
}
