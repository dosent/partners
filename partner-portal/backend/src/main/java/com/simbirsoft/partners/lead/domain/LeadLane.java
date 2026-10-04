package com.simbirsoft.partners.lead.domain;

import java.util.List;

public enum LeadLane {
    NEW(List.of(LeadStatus.NEW)),
    QUALIFICATION(List.of(LeadStatus.QUALIFICATION)),
    DUPLICATE_REVIEW(List.of(LeadStatus.DUPLICATE_REVIEW)),
    IN_WORK(List.of(LeadStatus.IN_WORK)),
    WON(List.of(LeadStatus.WON)),
    CLOSED(List.of(LeadStatus.LOST, LeadStatus.REJECTED));

    private final List<LeadStatus> statuses;
    LeadLane(List<LeadStatus> statuses) { this.statuses = statuses; }
    public List<LeadStatus> statuses() { return statuses; }
    public static LeadLane from(LeadStatus status) {
        return switch (status) {
            case NEW -> NEW;
            case QUALIFICATION -> QUALIFICATION;
            case DUPLICATE_REVIEW -> DUPLICATE_REVIEW;
            case IN_WORK -> IN_WORK;
            case WON -> WON;
            case LOST, REJECTED -> CLOSED;
        };
    }
}
