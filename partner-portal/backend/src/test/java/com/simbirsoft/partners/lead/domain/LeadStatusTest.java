package com.simbirsoft.partners.lead.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class LeadStatusTest {
    @Test
    void mapsTerminalStatusesToSharedClosedLaneWithoutLosingActualStatus() {
        assertThat(LeadStatus.LOST.lane()).isEqualTo(LeadLane.CLOSED);
        assertThat(LeadStatus.REJECTED.lane()).isEqualTo(LeadLane.CLOSED);
        assertThat(LeadStatus.LOST).isNotEqualTo(LeadStatus.REJECTED);
        assertThat(LeadLane.CLOSED.statuses()).containsExactly(LeadStatus.LOST, LeadStatus.REJECTED);
    }

    @Test
    void allowsOnlyDeclaredForwardProgressionAndTerminalStatesStayClosed() {
        assertThat(LeadStatus.NEW.canTransitionTo(LeadStatus.QUALIFICATION)).isTrue();
        assertThat(LeadStatus.NEW.canTransitionTo(LeadStatus.DUPLICATE_REVIEW)).isTrue();
        assertThat(LeadStatus.QUALIFICATION.canTransitionTo(LeadStatus.IN_WORK)).isTrue();
        assertThat(LeadStatus.IN_WORK.canTransitionTo(LeadStatus.WON)).isTrue();
        assertThat(LeadStatus.WON.canTransitionTo(LeadStatus.IN_WORK)).isFalse();
        assertThat(LeadStatus.LOST.canTransitionTo(LeadStatus.QUALIFICATION)).isFalse();
        assertThat(LeadStatus.REJECTED.canTransitionTo(LeadStatus.NEW)).isFalse();
        assertThat(LeadStatus.NEW.canTransitionTo(LeadStatus.NEW)).isFalse();
    }

    @Test
    void mapsActiveStatusesToTheirOwnLane() {
        assertThat(LeadLane.from(LeadStatus.NEW)).isEqualTo(LeadLane.NEW);
        assertThat(LeadLane.from(LeadStatus.QUALIFICATION)).isEqualTo(LeadLane.QUALIFICATION);
        assertThat(LeadLane.from(LeadStatus.DUPLICATE_REVIEW)).isEqualTo(LeadLane.DUPLICATE_REVIEW);
        assertThat(LeadLane.from(LeadStatus.IN_WORK)).isEqualTo(LeadLane.IN_WORK);
        assertThat(LeadLane.from(LeadStatus.WON)).isEqualTo(LeadLane.WON);
    }
}
