package com.simbirsoft.partners.staffing.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.access.application.StaffRole;
import com.simbirsoft.partners.access.application.StaffRoleLookup;
import com.simbirsoft.partners.audit.application.StaffingRequestAssignmentAudit;
import com.simbirsoft.partners.lead.application.LeadPartnerContextLookup;
import com.simbirsoft.partners.partner.application.PartnerProfileService;
import com.simbirsoft.partners.partner.application.PartnerProfileService.ProfileDetails;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffingRequestServiceTest {
    @Mock StaffingRequestStore requests;
    @Mock LeadPartnerContextLookup leads;
    @Mock PartnerProfileService profiles;
    @Mock StaffRoleLookup staffRoles;
    @Mock StaffingRequestAssignmentAudit audit;
    @InjectMocks StaffingRequestService service;

    @Test void staffQueueIncludesRequestsFromAnyOwner() {
        var page = new StaffingRequestStore.StaffPage(List.of(sample(UUID.randomUUID(), UUID.randomUUID())), 0, 20, 1);
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(requests.findStaffPage(0, 20)).thenReturn(page);
        assertThat(service.staffPage(new StaffPrincipal("actor"), 0, 20)).isEqualTo(page);
        verifyNoInteractions(profiles);
    }

    @Test void eligibleAssigneesComeFromResourceManagerGrants() {
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(staffRoles.usernamesWithRole(StaffRole.RESOURCE_MANAGER)).thenReturn(List.of("rm.one", "rm.two"));
        assertThat(service.assignees(new StaffPrincipal("actor"))).containsExactly("rm.one", "rm.two");
    }

    @Test void rejectsMissingStaffOrPermissionInApplicationLayer() {
        assertStatus(() -> service.staffPage(null, 0, 20), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> service.staffPage(new StaffPrincipal("viewer"), 0, 20), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.assignees(new StaffPrincipal("viewer")), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.assignResourceManager(new StaffPrincipal("viewer"), UUID.randomUUID(), null, 0), HttpStatus.FORBIDDEN);
        verifyNoInteractions(requests, audit);
    }

    @Test void rejectsInvalidPage() {
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        assertStatus(() -> service.staffPage(new StaffPrincipal("actor"), -1, 20), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.staffPage(new StaffPrincipal("actor"), 0, 101), HttpStatus.BAD_REQUEST);
    }

    @Test void assignsReassignsAndUnassignsWithCorrectAuditValues() {
        var initial = sample(UUID.randomUUID(), UUID.randomUUID());
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(staffRoles.hasRole("rm.one", StaffRole.RESOURCE_MANAGER)).thenReturn(true);
        when(staffRoles.hasRole("rm.two", StaffRole.RESOURCE_MANAGER)).thenReturn(true);
        when(requests.findAny(initial.id())).thenReturn(Optional.of(initial),
                Optional.of(initial.withAssignee("rm.one", Instant.now())),
                Optional.of(initial.withAssignee("rm.two", Instant.now())));
        when(requests.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), " RM.One ", 0)
                .assignedResourceManagerUsername()).isEqualTo("rm.one");
        assertThat(service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), "rm.two", 0)
                .assignedResourceManagerUsername()).isEqualTo("rm.two");
        assertThat(service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), null, 0)
                .assignedResourceManagerUsername()).isNull();
        verify(audit).record(eq(initial.id()), eq(StaffingRequestAssignmentAudit.Action.ASSIGNED), isNull(), eq("rm.one"), eq("actor"), any());
        verify(audit).record(eq(initial.id()), eq(StaffingRequestAssignmentAudit.Action.REASSIGNED), eq("rm.one"), eq("rm.two"), eq("actor"), any());
        verify(audit).record(eq(initial.id()), eq(StaffingRequestAssignmentAudit.Action.UNASSIGNED), eq("rm.two"), isNull(), eq("actor"), any());
    }

    @Test void repeatsDoNotWriteRequestOrAudit() {
        var initial = sample(UUID.randomUUID(), UUID.randomUUID());
        var assigned = initial.withAssignee("rm.one", Instant.now());
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(staffRoles.hasRole("rm.one", StaffRole.RESOURCE_MANAGER)).thenReturn(true);
        when(requests.findAny(initial.id())).thenReturn(Optional.of(assigned), Optional.of(initial));
        assertThat(service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), " RM.ONE ", 0)).isSameAs(assigned);
        assertThat(service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), null, 0)).isSameAs(initial);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void rejectsIneligibleAndInvalidUsernameWithoutWriting() {
        var initial = sample(UUID.randomUUID(), UUID.randomUUID());
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(requests.findAny(initial.id())).thenReturn(Optional.of(initial));
        for (String username : List.of("unknown", "partner.manager", " ", "bad\nusername", "a".repeat(256))) {
            assertStatus(() -> service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), username, 0), HttpStatus.BAD_REQUEST);
        }
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void rejectsMissingRequestAndStaleVersionWithoutWriting() {
        var initial = sample(UUID.randomUUID(), UUID.randomUUID());
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(requests.findAny(initial.id())).thenReturn(Optional.empty(), Optional.of(initial));
        assertStatus(() -> service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), null, 0), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), null, 1), HttpStatus.CONFLICT);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void optimisticLockRaceDoesNotRecordAudit() {
        var initial = sample(UUID.randomUUID(), UUID.randomUUID());
        when(staffRoles.hasPermission("actor", "staffing.request.manage")).thenReturn(true);
        when(staffRoles.hasRole("rm.one", StaffRole.RESOURCE_MANAGER)).thenReturn(true);
        when(requests.findAny(initial.id())).thenReturn(Optional.of(initial));
        when(requests.saveAndFlush(any())).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("race"));
        assertThatThrownBy(() -> service.assignResourceManager(new StaffPrincipal("actor"), initial.id(), "rm.one", 0))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        verifyNoInteractions(audit);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }

    @Test
    void replaysTheSavedRequestForTheSameOwnerAndIdempotencyKey() {
        PartnerPrincipal principal = new PartnerPrincipal("yandex", "partner-1");
        UUID owner = UUID.randomUUID(), key = UUID.randomUUID();
        var profile = mock(ProfileDetails.class);
        when(profile.id()).thenReturn(owner);
        when(profiles.current(principal)).thenReturn(profile);
        var saved = sample(owner, key);
        when(requests.findByIdempotencyKey(owner, key)).thenReturn(Optional.of(saved));

        assertThat(service.create(principal, key, command(saved.leadId()))).isEqualTo(saved);
        verify(requests, never()).save(any());
    }

    @Test
    void rejectsLeadThatDoesNotBelongToThePartner() {
        PartnerPrincipal principal = new PartnerPrincipal("yandex", "partner-1");
        UUID owner = UUID.randomUUID(), key = UUID.randomUUID(), lead = UUID.randomUUID();
        var profile = mock(ProfileDetails.class);
        when(profile.id()).thenReturn(owner);
        when(profiles.current(principal)).thenReturn(profile);
        when(requests.findByIdempotencyKey(owner, key)).thenReturn(Optional.empty());
        when(leads.findOwnedContext(lead, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(principal, key, command(lead)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithDifferentRequirements() {
        PartnerPrincipal principal = new PartnerPrincipal("yandex", "partner-1");
        UUID owner = UUID.randomUUID(), key = UUID.randomUUID();
        var profile = mock(ProfileDetails.class);
        when(profile.id()).thenReturn(owner);
        when(profiles.current(principal)).thenReturn(profile);
        var existing = sample(owner, key);
        when(requests.findByIdempotencyKey(owner, key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(principal, key, command(UUID.randomUUID())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void returnsTheWinningRequestWhenConcurrentCreationWinsTheUniqueKeyRace() {
        PartnerPrincipal principal = new PartnerPrincipal("yandex", "partner-1");
        UUID owner = UUID.randomUUID(), key = UUID.randomUUID(), lead = UUID.randomUUID();
        var winner = sample(owner, key, lead);
        var profile = mock(ProfileDetails.class);
        when(profile.id()).thenReturn(owner);
        when(profiles.current(principal)).thenReturn(profile);
        when(requests.findByIdempotencyKey(owner, key)).thenReturn(Optional.empty(), Optional.of(winner));
        when(leads.findOwnedContext(lead, owner)).thenReturn(Optional.of(new LeadPartnerContextLookup.LeadContext(lead, "Example", "Team")));
        when(requests.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        assertThat(service.create(principal, key, command(lead))).isEqualTo(winner);
    }

    private static StaffingRequestService.CreateCommand command(UUID lead) {
        return new StaffingRequestService.CreateCommand(lead,
                new StaffingRequest.RoleRequirements("Backend developer", 1, "Middle+", java.util.List.of("Java"),
                        java.util.List.of(), "Build services"),
                new StaffingRequest.WorkConditions(40, StaffingRequest.WorkMode.REMOTE, "Remote", true,
                        LocalDate.of(2026, 11, 1), false),
                new StaffingRequest.SelectionRequirements(null, null, true, false, true));
    }
    private static StaffingRequest sample(UUID owner, UUID key) {
        return sample(owner, key, UUID.randomUUID());
    }
    private static StaffingRequest sample(UUID owner, UUID key, UUID lead) {
        return StaffingRequest.create(UUID.randomUUID(), owner, lead, "SR-0001", key,
                new StaffingRequest.RoleRequirements("Backend developer", 1, "Middle+", java.util.List.of("Java"), java.util.List.of(), "Build services"),
                new StaffingRequest.WorkConditions(40, StaffingRequest.WorkMode.REMOTE, "Remote", true,
                        LocalDate.of(2026, 11, 1), false),
                new StaffingRequest.SelectionRequirements(null, null, true, false, true), java.time.Instant.now());
    }
}
