package com.simbirsoft.partners.access.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.simbirsoft.partners.access.application.LdapGroupRoleMapper;
import com.simbirsoft.partners.access.application.StaffAccessDeniedException;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffDirectoryUnavailableException;
import java.time.Duration;
import java.util.Map;
import javax.naming.AuthenticationException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.junit.jupiter.api.Test;

class JndiLdapStaffAuthenticatorTest {
    private static final String USER_DN = "uid=anna,ou=people,dc=example,dc=com";
    private static final String MANAGER_GROUP = "cn=partner managers,ou=groups,dc=example,dc=com";

    @Test
    void bindsWithSubmittedPasswordAndMapsDirectoryGroupToStaffRole() throws Exception {
        DirContext context = mock(DirContext.class);
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        when(connections.open(USER_DN, "directory-secret")).thenReturn(context);
        when(context.search(eq("ou=groups,dc=example,dc=com"),
                eq("(&(objectClass=groupOfNames)(member={0}))"), any(Object[].class), any(SearchControls.class)))
                .thenReturn(groups(MANAGER_GROUP));
        JndiLdapStaffAuthenticator authenticator = authenticator(connections);

        StaffIdentity identity = authenticator.authenticate("anna", "directory-secret");

        assertEquals("anna", identity.username());
        assertEquals(java.util.Set.of(StaffRole.PARTNER_MANAGER), identity.roles());
        verify(connections).open(USER_DN, "directory-secret");
        verify(context).search(eq("ou=groups,dc=example,dc=com"),
                eq("(&(objectClass=groupOfNames)(member={0}))"),
                aryEq(new Object[] {USER_DN}), any(SearchControls.class));
        verify(context).close();
    }

    @Test
    void rejectsLdapAuthenticatedUserWhoseGroupsDoNotGrantPortalRole() throws Exception {
        DirContext context = mock(DirContext.class);
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        when(connections.open(USER_DN, "directory-secret")).thenReturn(context);
        when(context.search(eq("ou=groups,dc=example,dc=com"),
                eq("(&(objectClass=groupOfNames)(member={0}))"), any(Object[].class), any(SearchControls.class)))
                .thenReturn(groups("cn=all employees,ou=groups,dc=example,dc=com"));

        assertThrows(StaffAccessDeniedException.class,
                () -> authenticator(connections).authenticate("anna", "directory-secret"));

        verify(context).close();
    }

    @Test
    void mapsDirectoryBindRejectionToGenericAuthenticationFailure() throws Exception {
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        when(connections.open(USER_DN, "wrong-password")).thenThrow(new AuthenticationException());

        assertThrows(StaffAuthenticationFailedException.class,
                () -> authenticator(connections).authenticate("anna", "wrong-password"));
    }

    @Test
    void disabledOrIncompleteDirectoryConfigurationCannotAuthenticate() {
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        var properties = new LdapProperties(false, "", "uid={0},ou=people,dc=example,dc=com",
                "", "(&(objectClass=groupOfNames)(member={0}))", Duration.ofSeconds(2), Duration.ofSeconds(3),
                Map.of("PARTNER_MANAGER", ""));
        var authenticator = new JndiLdapStaffAuthenticator(properties,
                new LdapGroupRoleMapper(properties.roleGroups()), connections);

        assertThrows(StaffDirectoryUnavailableException.class,
                () -> authenticator.authenticate("anna", "directory-secret"));
        verifyNoInteractions(connections);
    }

    private JndiLdapStaffAuthenticator authenticator(LdapConnectionFactory connections) {
        var properties = new LdapProperties(true, "ldap://directory.example:389",
                "uid={0},ou=people,dc=example,dc=com", "ou=groups,dc=example,dc=com",
                "(&(objectClass=groupOfNames)(member={0}))", Duration.ofSeconds(2), Duration.ofSeconds(3),
                Map.of("PARTNER_MANAGER", "CN=Partner Managers,OU=Groups,DC=example,DC=com"));
        return new JndiLdapStaffAuthenticator(properties, new LdapGroupRoleMapper(properties.roleGroups()), connections);
    }

    @SafeVarargs
    private final NamingEnumeration<SearchResult> groups(String... dns) {
        return new NamingEnumeration<>() {
            private final java.util.Iterator<SearchResult> iterator = java.util.Arrays.stream(dns)
                    .map(dn -> {
                        var result = new SearchResult(dn, null, null);
                        result.setNameInNamespace(dn);
                        return result;
                    }).iterator();

            @Override public SearchResult next() { return iterator.next(); }
            @Override public boolean hasMore() { return iterator.hasNext(); }
            @Override public void close() { }
            @Override public boolean hasMoreElements() { return iterator.hasNext(); }
            @Override public SearchResult nextElement() { return iterator.next(); }
        };
    }
}
