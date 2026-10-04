package com.simbirsoft.partners.lead.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum LeadStatus {
    NEW, QUALIFICATION, DUPLICATE_REVIEW, IN_WORK, WON, LOST, REJECTED;

    private static final Map<LeadStatus, Set<LeadStatus>> TRANSITIONS = Map.of(
            NEW, EnumSet.of(QUALIFICATION, DUPLICATE_REVIEW, REJECTED),
            QUALIFICATION, EnumSet.of(DUPLICATE_REVIEW, IN_WORK, LOST, REJECTED),
            DUPLICATE_REVIEW, EnumSet.of(QUALIFICATION, IN_WORK, LOST, REJECTED),
            IN_WORK, EnumSet.of(WON, LOST),
            WON, EnumSet.noneOf(LeadStatus.class),
            LOST, EnumSet.noneOf(LeadStatus.class),
            REJECTED, EnumSet.noneOf(LeadStatus.class));

    public LeadLane lane() { return LeadLane.from(this); }
    public boolean canTransitionTo(LeadStatus target) {
        return target != null && TRANSITIONS.get(this).contains(target);
    }
}
