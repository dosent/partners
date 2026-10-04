package com.simbirsoft.partners.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.simbirsoft.partners.access.application.IdentityProviderFailureException;
import com.simbirsoft.partners.access.application.StaffAccessService;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffRole;
import java.util.Set;
import org.junit.jupiter.api.Test;

class E2eAuthenticationConfigurationTest {
    private final E2eAuthenticationConfiguration configuration = new E2eAuthenticationConfiguration();

    @Test
    void acceptsOnlySyntheticStaffNamesAndFixedPassword() {
        StaffAccessService access = mock(StaffAccessService.class);
        when(access.rolesFor("ivan.neshin")).thenReturn(Set.of(StaffRole.SYSTEM_ADMIN));
        when(access.rolesFor("e2e-staff-7")).thenReturn(Set.of());
        var authenticator = configuration.e2eStaffAuthenticator(access);

        assertEquals(Set.of(StaffRole.SYSTEM_ADMIN),
                authenticator.authenticate("ivan.neshin", E2eAuthenticationConfiguration.STAFF_PASSWORD).roles());
        assertEquals(Set.of(),
                authenticator.authenticate("e2e-staff-7", E2eAuthenticationConfiguration.STAFF_PASSWORD).roles());
        for (String rejected : new String[] {"anna", "e2e-", "e2e-A", "e2e_staff"}) {
            assertThrows(StaffAuthenticationFailedException.class,
                    () -> authenticator.authenticate(rejected, E2eAuthenticationConfiguration.STAFF_PASSWORD));
        }
        assertThrows(StaffAuthenticationFailedException.class,
                () -> authenticator.authenticate("ivan.neshin", "wrong-password"));
    }

    @Test
    void exchangesOnlySyntheticPartnerCodes() {
        var provider = configuration.e2eYandexIdentityProvider();

        assertEquals("e2e-partner-42", provider.exchangeAuthorizationCode("e2e-partner-42").subject());
        assertEquals("YANDEX", provider.exchangeAuthorizationCode("e2e-partner-42").provider());
        for (String rejected : new String[] {"real-oauth-code", "e2e-partner-", "e2e-partner-X"}) {
            assertThrows(IdentityProviderFailureException.class,
                    () -> provider.exchangeAuthorizationCode(rejected));
        }
    }
}
