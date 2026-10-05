package com.simbirsoft.partners.staffing.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffingRequestServiceTest {
    @Mock StaffingRequestStore requests;
    @Mock LeadPartnerContextLookup leads;
    @Mock PartnerProfileService profiles;
    @InjectMocks StaffingRequestService service;

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
