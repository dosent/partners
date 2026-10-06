package com.simbirsoft.partners.access.api;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.access.application.StaffAccessDeniedException;
import com.simbirsoft.partners.access.application.PartnerApiKeyStore;
import com.simbirsoft.partners.access.application.StaffAuthenticator;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffRoleGrantStore;
import com.simbirsoft.partners.audit.application.PartnerProfileChangeAudit;
import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import com.simbirsoft.partners.access.infrastructure.LdapConnectionFactory;
import com.simbirsoft.partners.partner.application.ApprovedPartnerProfileSource;
import com.simbirsoft.partners.partner.application.PartnerProfileStore;
import com.simbirsoft.partners.localization.application.RegistrationContentStore;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationStore;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "partners.auth.ldap.enabled=false",
        "partners.auth.ldap.url=ldaps://directory.example:636",
        "partners.auth.ldap.user-dn-pattern=uid={0},ou=people,dc=example,dc=com",
        "partners.auth.ldap.group-search-base=ou=groups,dc=example,dc=com",
        "partners.auth.ldap.group-search-filter=(&(objectClass=groupOfNames)(member={0}))",
        "partners.auth.ldap.role-groups.PARTNER_MANAGER=CN=Partner Managers,OU=Groups,DC=example,DC=com"
})
@ActiveProfiles("auth-test")
@AutoConfigureMockMvc
class StaffLdapLoginFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private StaffAuthenticator authenticator;
    @MockitoBean(name = "jpaStaffRoleGrantStore") private StaffRoleGrantStore roleGrants;
    @MockitoBean(name = "jndiLdapConnectionFactory") private LdapConnectionFactory ldapConnectionFactory;
    @MockitoBean(name = "jpaRegistrationContentStore") private RegistrationContentStore registrationContentStore;
    @MockitoBean private PartnerApplicationStore partnerApplicationStore;
    @MockitoBean(name = "jpaPartnerProfileStore") private PartnerProfileStore partnerProfileStore;
    @MockitoBean(name = "jpaPartnerApiKeyStore") private PartnerApiKeyStore partnerApiKeyStore;
    @MockitoBean(name = "jpaApprovedPartnerProfileSource") private ApprovedPartnerProfileSource approvedPartnerProfileSource;
    @MockitoBean private PartnerProfileChangeAudit partnerProfileChangeAudit;
    @MockitoBean private StaffingRequestAssignmentAudit assignmentAudit;

    @Test
    void csrfBootstrapExposesTheHeaderNameForBrowserLogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void allowedStaffLoginCreatesRotatedSessionWithoutPasswordCredentials() throws Exception {
        given(authenticator.authenticate(eq("anna"), eq("directory-secret")))
                .willReturn(new StaffIdentity("anna", Set.of(StaffRole.PARTNER_MANAGER)));
        given(roleGrants.rolesFor("anna")).willReturn(Set.of(StaffRole.PARTNER_MANAGER));
        MvcResult anonymous = mockMvc.perform(get("/api/v1/auth/staff/session"))
                .andExpect(status().isUnauthorized()).andReturn();
        MockHttpSession oldSession = (MockHttpSession) anonymous.getRequest().getSession(true);
        String oldSessionId = oldSession.getId();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/staff/login")
                        .with(csrf())
                        .session(oldSession)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", "anna", "password", "directory-secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anna"))
                .andExpect(jsonPath("$.roles[0]").value("PARTNER_MANAGER"))
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        assertNotEquals(oldSessionId, authenticatedSession.getId());
        SecurityContext context = (SecurityContext) authenticatedSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNull(context.getAuthentication().getCredentials());

        mockMvc.perform(get("/api/v1/auth/staff/session").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anna"));
        mockMvc.perform(get("/api/v1/auth/yandex/session").session(authenticatedSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffLoginCreatesAndRotatesSessionWhenBrowserHasNoneYet() throws Exception {
        given(authenticator.authenticate(eq("anna"), eq("directory-secret")))
                .willReturn(new StaffIdentity("anna", Set.of(StaffRole.PARTNER_MANAGER)));
        given(roleGrants.rolesFor("anna")).willReturn(Set.of(StaffRole.PARTNER_MANAGER));

        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        assertNull(csrf.getRequest().getSession(false));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/staff/login").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", "anna", "password", "directory-secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anna"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNull(context.getAuthentication().getCredentials());
    }

    @Test
    void ldapIdentityWithoutMappedRoleIsForbidden() throws Exception {
        given(authenticator.authenticate(eq("anna"), eq("directory-secret")))
                .willThrow(new StaffAccessDeniedException());

        mockMvc.perform(post("/api/v1/auth/staff/login").with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", "anna", "password", "directory-secret"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectedLdapCredentialsReturnGenericUnauthorizedResponse() throws Exception {
        given(authenticator.authenticate(eq("anna"), eq("wrong")))
                .willThrow(new StaffAuthenticationFailedException());

        mockMvc.perform(post("/api/v1/auth/staff/login").with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(Map.of("username", "anna", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }
}
