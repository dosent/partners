package com.simbirsoft.partners.lead.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.lead.domain.Lead;
import com.simbirsoft.partners.lead.domain.LeadMessage;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {
    @Mock LeadStore store;
    @Mock LeadPartnerIdentity identity;
    PartnerPrincipal principal = new PartnerPrincipal("yandex", "subject-1");
    UUID owner = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    LeadService service;

    @BeforeEach void setUp() { service = new LeadService(store, identity); lenient().when(identity.profileId(principal)).thenReturn(owner); }

    @Test
    void partnerMessageProjectionExcludesInternalNotesEvenIfStoreReturnsThem() {
        when(store.findOwned(leadId, owner)).thenReturn(Optional.of(lead(leadId, owner)));
        when(store.publicMessages(leadId)).thenReturn(List.of(
                new LeadMessage(UUID.randomUUID(), leadId, "STAFF", "Visible reply", false, Instant.parse("2026-01-01T00:00:00Z")),
                new LeadMessage(UUID.randomUUID(), leadId, "STAFF", "Internal investigation note", true, Instant.parse("2026-01-02T00:00:00Z"))));
        var visible = service.messages(principal, leadId);
        assertThat(visible).extracting(LeadMessage::body).containsExactly("Visible reply");
        assertThat(visible).allSatisfy(message -> assertThat(message.internal()).isFalse());
    }

    @Test
    void boardContainsSixLanesAndClosedLaneCombinesTerminalStatusesOnce() {
        when(store.count(any(), anyList(), any(), any(), any())).thenReturn(0L);
        when(store.findPage(any(), anyList(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new LeadStore.Page(List.of(), false));
        var board=service.board(principal,null,null,null,null,20,List.of());
        assertThat(board.columns()).hasSize(6);
        var closed=board.columns().get(5);
        assertThat(closed.lane()).isEqualTo(com.simbirsoft.partners.lead.domain.LeadLane.CLOSED);
        assertThat(closed.statuses()).containsExactly(LeadStatus.LOST,LeadStatus.REJECTED);
        assertThat(closed.total()).isZero();
    }

    @Test
    void staffStatusTransitionUsesPrincipalUsernameAndReturnsUpdatedLead() {
        var before=lead(leadId,owner);
        var after=new Lead(leadId,owner,before.number(),before.companyName(),before.domainOrTaxId(),before.requestSummary(),
                before.budgetAmount(),before.budgetCurrency(),before.plannedStartAt(),before.decisionMakerName(),
                before.decisionMakerEmail(),before.description(),LeadStatus.QUALIFICATION,before.createdAt(),Instant.now());
        when(store.findAny(leadId)).thenReturn(Optional.of(before));
        when(store.changeStatus(eq(leadId),eq(LeadStatus.NEW),eq(LeadStatus.QUALIFICATION),eq("manager"),any())).thenReturn(after);
        var changed=service.changeStatus(leadId,LeadStatus.QUALIFICATION,new StaffPrincipal("manager"));
        assertThat(changed.status()).isEqualTo(LeadStatus.QUALIFICATION);
        verify(store).changeStatus(eq(leadId),eq(LeadStatus.NEW),eq(LeadStatus.QUALIFICATION),eq("manager"),any());
    }

    @Test
    void rejectsForbiddenOrTerminalStatusTransitions() {
        when(store.findAny(leadId)).thenReturn(Optional.of(lead(leadId,owner)));
        assertThatThrownBy(() -> service.changeStatus(leadId,LeadStatus.WON,new StaffPrincipal("manager")))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode.value").isEqualTo(409);
        verify(store,never()).changeStatus(any(),any(),any(),any(),any());
    }

    @Test
    void acceptsBudgetAtNumericSixteenTwoBoundary() {
        var command=createLead(new BigDecimal("99999999999999.99"));
        var saved=lead(leadId,owner);
        when(store.save(any())).thenReturn(saved);
        assertThat(service.create(principal,command)).isEqualTo(saved);
        verify(store).save(any());
    }

    @Test
    void rejectsInvalidBudgetPrecisionAndScaleBeforePersistence() {
        var tooManyIntegerDigits=createLead(new BigDecimal("100000000000000.00"));
        assertThatThrownBy(() -> service.create(principal,tooManyIntegerDigits))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode.value").isEqualTo(400);
        var tooManyFractionDigits=createLead(new BigDecimal("1.001"));
        assertThatThrownBy(() -> service.create(principal,tooManyFractionDigits))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode.value").isEqualTo(400);
        var extraTrailingFractionDigits=createLead(new BigDecimal("1.000"));
        assertThatThrownBy(() -> service.create(principal,extraTrailingFractionDigits))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode.value").isEqualTo(400);
        verify(store,never()).save(any());
    }

    @Test
    void rejectsMissingRequiredFieldsBeforePersistence() {
        var invalid=new LeadService.CreateLead(" ",null,"Request",null,null,null,null,null,"Details");
        assertThatThrownBy(() -> service.create(principal,invalid))
                .isInstanceOf(ResponseStatusException.class).extracting("statusCode.value").isEqualTo(400);
        verify(store,never()).save(any());
    }

    @Test
    void foreignAndUnknownLeadIdsHaveTheSameNotFoundOutcome() {
        when(store.findOwned(leadId, owner)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(principal, leadId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value").isEqualTo(404);
    }

    private static LeadService.CreateLead createLead(BigDecimal budget) {
        return new LeadService.CreateLead("Acme",null,"Request",budget,"USD",LocalDate.parse("2026-11-01"),
                null,null,"Details");
    }

    private static Lead lead(UUID id, UUID owner) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        return new Lead(id, owner, "L-1234", "Acme", "acme.test", "Need", null, null, null,
                null, null, "Details", LeadStatus.NEW, now, now);
    }
}
