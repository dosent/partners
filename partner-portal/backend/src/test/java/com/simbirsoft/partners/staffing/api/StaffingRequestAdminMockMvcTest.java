package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.access.application.*;
import com.simbirsoft.partners.access.infrastructure.SecurityConfiguration;
import com.simbirsoft.partners.access.infrastructure.StaffAuthenticationToken;
import com.simbirsoft.partners.access.infrastructure.PartnerAuthenticationToken;
import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import com.simbirsoft.partners.lead.application.LeadPartnerContextLookup;
import com.simbirsoft.partners.partner.application.PartnerProfileService;
import com.simbirsoft.partners.staffing.application.*;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {StaffingRequestAdminController.class, PartnerStaffingRequestController.class})
@Import({SecurityConfiguration.class, StaffingRequestService.class})
class StaffingRequestAdminMockMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean StaffingRequestStore requests;
    @MockitoBean LeadPartnerContextLookup leads;
    @MockitoBean PartnerProfileService profiles;
    @MockitoBean StaffAccessService staffAccess;
    @MockitoBean StaffingRequestAssignmentAudit audit;
    @MockitoBean PartnerApiKeyService apiKeys;
    @MockitoBean StaffingRequestAttachmentPort attachments;
    private final StaffingRequest request = StaffingRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "SR-TEST", UUID.randomUUID(), new StaffingRequest.RoleRequirements("Java developer", 2, "Middle", List.of("Java"), List.of(), "Build services"),
            new StaffingRequest.WorkConditions(40, StaffingRequest.WorkMode.REMOTE, "Russia", false, null, true),
            new StaffingRequest.SelectionRequirements(null, null, true, false, true), Instant.parse("2026-10-06T12:00:00Z"));
    private static final String BASE = "/api/v1/admin/staffing-requests";

    @BeforeEach void setup() {
        when(staffAccess.rolesFor("actor")).thenReturn(Set.of(StaffRole.RESOURCE_MANAGER));
        when(staffAccess.rolesFor("viewer")).thenReturn(Set.of(StaffRole.PARTNER_MANAGER));
        when(staffAccess.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(staffAccess.hasRole("rm.one", StaffRole.RESOURCE_MANAGER)).thenReturn(true);
        when(requests.findAny(request.id())).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void queueHasOnlyAssignmentContextAndVersion() throws Exception {
        when(requests.findStaffPage(0, 20)).thenReturn(new StaffingRequestStore.StaffPage(List.of(request), 0, 20, 1));
        mvc.perform(get(BASE).with(authentication(staff("actor")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Java developer"))
                .andExpect(jsonPath("$.items[0].headcount").value(2))
                .andExpect(jsonPath("$.items[0].version").value(0))
                .andExpect(jsonPath("$.items[0].assignedResourceManagerUsername").isEmpty())
                .andExpect(jsonPath("$.items[0].ownerProfileId").doesNotExist())
                .andExpect(jsonPath("$.items[0].selection").doesNotExist())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test void assigneesExposeOnlyEligibleUsernameAndRole() throws Exception {
        when(staffAccess.usernamesWithRole(StaffRole.RESOURCE_MANAGER)).thenReturn(List.of("rm.one"));
        mvc.perform(get(BASE + "/assignees").with(authentication(staff("actor")))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("rm.one"))
                .andExpect(jsonPath("$[0].role").value("RESOURCE_MANAGER"));
    }

    @Test void assignmentAcceptsUsernameAndNull() throws Exception {
        mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("actor"))).with(csrf())
                .contentType("application/json").content("{\"username\":\" RM.ONE \",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedResourceManagerUsername").value("rm.one"));
        mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("actor"))).with(csrf())
                .contentType("application/json").content("{\"username\":null,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedResourceManagerUsername").isEmpty());
    }

    @Test void rejectsUnknownOrNonResourceManager() throws Exception {
        for (String username : List.of("unknown", "partner.manager")) {
            mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("actor"))).with(csrf())
                    .contentType("application/json").content("{\"username\":\"" + username + "\",\"expectedVersion\":0}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(audit);
    }

    @Test void requiresExpectedVersionAndValidPagination() throws Exception {
        for (String payload : List.of("{\"username\":null}", "{\"username\":null,\"expectedVersion\":-1}")) {
            mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("actor"))).with(csrf())
                    .contentType("application/json").content(payload)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get(BASE).param("size", "101").with(authentication(staff("actor"))))
                .andExpect(status().isBadRequest());
    }

    @Test void missingRequestIs404AndBothVersionConflictsAre409() throws Exception {
        mvc.perform(put(BASE + "/" + UUID.randomUUID() + "/assignment").with(authentication(staff("actor"))).with(csrf())
                .contentType("application/json").content("{\"username\":null,\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("actor"))).with(csrf())
                .contentType("application/json").content("{\"username\":null,\"expectedVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        when(requests.saveAndFlush(any())).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("race"));
        mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("actor"))).with(csrf())
                .contentType("application/json").content("{\"username\":\"rm.one\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        verifyNoInteractions(audit);
    }

    @Test void staffRoutesRequireAuthenticationAndPermission() throws Exception {
        for (String path : List.of(BASE, BASE + "/assignees")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(authentication(staff("viewer")))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(authentication(new PartnerAuthenticationToken(new PartnerPrincipal("yandex", "p1")))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(csrf()).contentType("application/json")
                .content("{\"username\":null,\"expectedVersion\":0}")).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("viewer"))).with(csrf())
                .contentType("application/json").content("{\"username\":null,\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test void mutationsRequireCsrf() throws Exception {
        mvc.perform(put(BASE + "/" + request.id() + "/assignment").with(authentication(staff("actor")))
                .contentType("application/json").content("{\"username\":\"rm.one\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(audit);
    }

    @Test void partnerResponseNeverDisclosesAssigneeOrAudit() throws Exception {
        var partner = new PartnerPrincipal("yandex", "p1");
        var profile = mock(PartnerProfileService.ProfileDetails.class);
        when(profile.id()).thenReturn(request.ownerProfileId());
        when(profiles.current(partner)).thenReturn(profile);
        var assigned = request.withAssignee("rm.one", Instant.now());
        when(requests.findOwned(request.id(), request.ownerProfileId())).thenReturn(Optional.of(assigned));
        when(requests.findOwnedPage(request.ownerProfileId(), 0, 20)).thenReturn(List.of(assigned));
        for (String path : List.of("/api/v1/partner/staffing-requests", "/api/v1/partner/staffing-requests/" + request.id())) {
            mvc.perform(get(path).with(authentication(new PartnerAuthenticationToken(partner)))).andExpect(status().isOk())
                    .andExpect(jsonPath("$..assignedResourceManagerUsername").isEmpty())
                    .andExpect(jsonPath("$..assignmentHistory").isEmpty());
        }
    }

    private static StaffAuthenticationToken staff(String username) {
        return new StaffAuthenticationToken(username, Set.of(StaffRole.RESOURCE_MANAGER));
    }
}
