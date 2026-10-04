package com.simbirsoft.partners.access.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.simbirsoft.partners.access.application.StaffAccessService;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffDirectoryUnavailableException;
import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffRole;
import java.time.Duration;
import java.util.Set;
import javax.naming.AuthenticationException;
import javax.naming.directory.DirContext;
import org.junit.jupiter.api.Test;

class JndiLdapStaffAuthenticatorTest {
    private static final String USER_DN = "uid=anna,ou=people,dc=example,dc=com";

    @Test
    void bindUsesSubmittedPasswordAndLoadsAppOwnedRoles() throws Exception {
        DirContext context = mock(DirContext.class);
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        StaffAccessService access = mock(StaffAccessService.class);
        when(connections.open(USER_DN, "directory-secret")).thenReturn(context);
        when(access.rolesFor("anna")).thenReturn(Set.of(StaffRole.PARTNER_MANAGER));

        StaffIdentity identity = authenticator(configuredProperties(), access, connections)
                .authenticate("anna", "directory-secret");

        assertEquals("anna", identity.username());
        assertEquals(Set.of(StaffRole.PARTNER_MANAGER), identity.roles());
        verify(connections).open(USER_DN, "directory-secret");
        verify(access).rolesFor("anna");
        verify(context).close();
    }

    @Test
    void authenticatedStaffWithoutAppOwnedRolesRemainsPending() throws Exception {
        DirContext context = mock(DirContext.class);
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        StaffAccessService access = mock(StaffAccessService.class);
        when(connections.open(USER_DN, "directory-secret")).thenReturn(context);
        when(access.rolesFor("anna")).thenReturn(Set.of());

        StaffIdentity identity = authenticator(configuredProperties(), access, connections)
                .authenticate("anna", "directory-secret");

        assertEquals(Set.of(), identity.roles());
        verify(context).close();
    }

    @Test
    void bindRejectionIsAuthenticationFailure() throws Exception {
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        StaffAccessService access = mock(StaffAccessService.class);
        when(connections.open(USER_DN, "wrong-password")).thenThrow(new AuthenticationException());

        assertThrows(StaffAuthenticationFailedException.class,
                () -> authenticator(configuredProperties(), access, connections)
                        .authenticate("anna", "wrong-password"));
        verifyNoInteractions(access);
    }

    @Test
    void disabledLdapCannotAuthenticate() {
        LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
        LdapProperties properties = new LdapProperties(false, "ldaps://directory.example:636",
                "ou=people,dc=example,dc=com", "/tmp/e2e-ca.crt",
                Duration.ofSeconds(2), Duration.ofSeconds(3));

        assertThrows(StaffDirectoryUnavailableException.class,
                () -> authenticator(properties, mock(StaffAccessService.class), connections)
                        .authenticate("anna", "directory-secret"));
        verifyNoInteractions(connections);
    }

    @Test
    void plaintextOrMixedLdapUrlsCannotAuthenticate() {
        for (String url : new String[] {"ldap://directory.example:389",
                "ldaps://directory.example:636 ldap://fallback.example:389"}) {
            LdapConnectionFactory connections = mock(LdapConnectionFactory.class);
            LdapProperties properties = new LdapProperties(true, url,
                    "ou=people,dc=example,dc=com", "/tmp/e2e-ca.crt",
                    Duration.ofSeconds(2), Duration.ofSeconds(3));

            assertFalse(properties.configured());
            assertThrows(StaffDirectoryUnavailableException.class,
                    () -> authenticator(properties, mock(StaffAccessService.class), connections)
                            .authenticate("anna", "directory-secret"));
            verifyNoInteractions(connections);
        }
    }

    private static LdapProperties configuredProperties() {
        return new LdapProperties(true, "ldaps://directory.example:636",
                "ou=people,dc=example,dc=com", "/tmp/e2e-ca.crt",
                Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private static JndiLdapStaffAuthenticator authenticator(LdapProperties properties,
            StaffAccessService access, LdapConnectionFactory connections) {
        return new JndiLdapStaffAuthenticator(properties, access, connections);
    }
}
