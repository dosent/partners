package com.simbirsoft.partners.access.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simbirsoft.partners.access.application.PartnerApiKeyService;
import com.simbirsoft.partners.access.application.PartnerApiKeyStore;
import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.access.application.StaffRoleGrantStore;
import com.simbirsoft.partners.access.application.YandexIdentityProvider;
import com.simbirsoft.partners.access.infrastructure.StaffRoleGrantRepository;
import com.simbirsoft.partners.access.infrastructure.StaffRoleAuditRepository;
import com.simbirsoft.partners.partner.application.PartnerProfileStore;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationStore;
import com.simbirsoft.partners.audit.application.PartnerProfileChangeAudit;
import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import com.simbirsoft.partners.localization.application.RegistrationContentStore;
import com.simbirsoft.partners.audit.infrastructure.PartnerProfileAuditRepository;
import com.simbirsoft.partners.access.infrastructure.PartnerAuthenticationToken;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "partners.auth.yandex.enabled=false"
})
@ActiveProfiles("auth-test")
@AutoConfigureMockMvc
class PartnerApiKeyAuthenticationMockMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean PartnerApiKeyService service;
    @MockitoBean PartnerApiKeyStore store;
    @MockitoBean PartnerProfileAuditRepository profileAuditRepository;
    @MockitoBean(name = "jpaStaffRoleGrantStore") StaffRoleGrantStore roleGrants;
    @MockitoBean StaffRoleGrantRepository roleGrantRepository;
    @MockitoBean StaffRoleAuditRepository roleAuditRepository;
    @MockitoBean(name = "jpaRegistrationContentStore") RegistrationContentStore registrationContentStore;
    @MockitoBean PartnerApplicationStore partnerApplicationStore;
    @MockitoBean(name = "jpaPartnerProfileStore") PartnerProfileStore partnerProfileStore;
    @MockitoBean(name = "jpaApprovedPartnerProfileSource") com.simbirsoft.partners.partner.application.ApprovedPartnerProfileSource approvedPartnerProfileSource;
    @MockitoBean PartnerProfileChangeAudit profileChangeAudit;
    @MockitoBean StaffingRequestAssignmentAudit assignmentAudit;
    @MockitoBean YandexIdentityProvider identityProvider;

    @Test void apiKeyCanCheckMcpAccess() throws Exception {
        UUID id=UUID.randomUUID();
        when(service.authenticate("valid-key")).thenReturn(Optional.of(new PartnerApiKeyService.ApiKeyIdentity(id, "yandex", "p1")));
        mvc.perform(get("/api/v1/partner/mcp/access").header("X-Partner-API-Key", "valid-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.keyId").value(id.toString()));
    }

    @Test void invalidKeyCannotFallBackToValidPartnerSession() throws Exception {
        when(service.authenticate("bad-key")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/partner/mcp/access").header("X-Partner-API-Key", "bad-key")
                        .with(authentication(new PartnerAuthenticationToken(new PartnerPrincipal("yandex", "p1")))))
                .andExpect(status().isUnauthorized());
    }

    @Test void keyManagementRequiresWebSessionAuthority() throws Exception {
        mvc.perform(get("/api/v1/partner/api-keys")
                        .with(authentication(new com.simbirsoft.partners.access.infrastructure.PartnerApiKeyAuthenticationToken(
                                UUID.randomUUID(), new PartnerPrincipal("yandex", "p1")))))
                .andExpect(status().isForbidden());
    }

    @Test void keyManagementWorksWithPartnerWebSession() throws Exception {
        when(service.list(any())).thenReturn(java.util.List.of());
        mvc.perform(get("/api/v1/partner/api-keys")
                        .with(authentication(new PartnerAuthenticationToken(new PartnerPrincipal("yandex", "p1")))))
                .andExpect(status().isOk());
    }

    @Test void missingKeyCannotCheckMcpAccess() throws Exception {
        mvc.perform(get("/api/v1/partner/mcp/access")).andExpect(status().isUnauthorized());
    }

    @Test void invalidKeyStopsMutationBeforeCsrfFallback() throws Exception {
        when(service.authenticate("bad-key")).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/partner/api-keys").header("X-Partner-API-Key", "bad-key")
                        .with(csrf()).contentType("application/json").content("{\"name\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }
}
