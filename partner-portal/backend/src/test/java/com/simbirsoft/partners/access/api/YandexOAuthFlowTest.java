package com.simbirsoft.partners.access.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simbirsoft.partners.access.application.ExternalIdentity;
import com.simbirsoft.partners.access.application.YandexIdentityProvider;
import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.audit.application.PartnerProfileChangeAudit;
import com.simbirsoft.partners.access.application.StaffRoleGrantStore;
import com.simbirsoft.partners.access.infrastructure.StaffRoleGrantRepository;
import com.simbirsoft.partners.access.infrastructure.StaffRoleAuditRepository;
import com.simbirsoft.partners.localization.application.RegistrationContentStore;
import com.simbirsoft.partners.partner.application.ApprovedPartnerProfileSource;
import com.simbirsoft.partners.partner.application.PartnerProfileStore;
import com.simbirsoft.partners.partner.infrastructure.PartnerApplicationStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "partners.auth.yandex.enabled=true",
        "partners.auth.yandex.client-id=test-client-id",
        "partners.auth.yandex.client-secret=test-client-secret",
        "partners.auth.yandex.redirect-uri=http://localhost/api/v1/auth/yandex/callback"
})
@ActiveProfiles("auth-test")
@AutoConfigureMockMvc
class YandexOAuthFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YandexIdentityProvider identityProvider;

    @MockitoBean(name = "jpaStaffRoleGrantStore")
    private StaffRoleGrantStore roleGrants;

    @MockitoBean
    private StaffRoleGrantRepository roleGrantRepository;

    @MockitoBean
    private StaffRoleAuditRepository roleAuditRepository;

    @MockitoBean(name = "jpaRegistrationContentStore")
    private RegistrationContentStore registrationContentStore;

    @MockitoBean
    private PartnerApplicationStore partnerApplicationStore;

    @MockitoBean(name = "jpaPartnerProfileStore")
    private PartnerProfileStore partnerProfileStore;

    @MockitoBean(name = "jpaApprovedPartnerProfileSource")
    private ApprovedPartnerProfileSource approvedPartnerProfileSource;

    @MockitoBean
    private PartnerProfileChangeAudit partnerProfileChangeAudit;

    @Autowired
    private Environment environment;

    @Test
    void loginStartRedirectsToYandexWithOAuthState() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/yandex/start"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://oauth.yandex.ru/authorize")))
                .andExpect(header().string("Location", containsString("client_id=test-client-id")))
                .andExpect(header().string("Location", containsString("scope=login:info")))
                .andReturn();

        String state = stateFrom(result);
        assertEquals(43, state.length());
        assertEquals(Boolean.TRUE, environment.getProperty("server.servlet.session.cookie.http-only", Boolean.class));
        assertEquals(Boolean.TRUE, environment.getProperty("server.servlet.session.cookie.secure", Boolean.class));
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"));
    }

    @Test
    void callbackWithValidStateCreatesSessionContainingOnlyYandexIdentity() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/v1/auth/yandex/start")).andReturn();
        MockHttpSession session = (MockHttpSession) started.getRequest().getSession(false);
        String state = stateFrom(started);
        String oldSessionId = session.getId();
        given(identityProvider.exchangeAuthorizationCode(eq("test-code")))
                .willReturn(new ExternalIdentity("YANDEX", "yandex-subject-7"));

        mockMvc.perform(get("/api/v1/auth/yandex/callback")
                        .session(session)
                        .param("code", "test-code")
                        .param("state", state))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/"));
        assertNotEquals(oldSessionId, session.getId());
        SecurityContext securityContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertEquals("yandex-subject-7", ((PartnerPrincipal) securityContext.getAuthentication().getPrincipal()).subject());
        assertNull(securityContext.getAuthentication().getCredentials());

        mockMvc.perform(get("/api/v1/auth/yandex/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("YANDEX"))
                .andExpect(jsonPath("$.subject").value("yandex-subject-7"));
    }

    @Test
    void callbackWithMismatchedStateDoesNotAuthenticate() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/v1/auth/yandex/start")).andReturn();
        MockHttpSession session = (MockHttpSession) started.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/yandex/callback")
                        .session(session)
                        .param("code", "test-code")
                        .param("state", "mismatched-state"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(identityProvider);
        mockMvc.perform(get("/api/v1/auth/yandex/session").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerSessionEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/yandex/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void logoutInvalidatesPartnerSession() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/v1/auth/yandex/start")).andReturn();
        MockHttpSession session = (MockHttpSession) started.getRequest().getSession(false);
        given(identityProvider.exchangeAuthorizationCode(eq("logout-code")))
                .willReturn(new ExternalIdentity("YANDEX", "logout-subject"));

        MvcResult login = mockMvc.perform(get("/api/v1/auth/yandex/callback")
                        .session(session)
                        .param("code", "logout-code")
                        .param("state", stateFrom(started)))
                .andExpect(status().isSeeOther())
                .andReturn();
        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/yandex/logout").session(authenticatedSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/yandex/session").session(authenticatedSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/yandex/logout").with(csrf()).session(authenticatedSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/yandex/session").session(authenticatedSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerLogoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/yandex/logout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private String stateFrom(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
    }
}
