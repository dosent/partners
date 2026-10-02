package com.simbirsoft.partners.access.application;

public enum StaffPermission {
    PARTNER_APPLICATION_READ("partner.application.read"),
    PARTNER_APPLICATION_DECIDE("partner.application.decide"),
    LEAD_MANAGE("lead.manage"),
    STAFFING_REQUEST_MANAGE("staffing.request.manage"),
    CANDIDATE_MANAGE("candidate.manage"),
    FINANCE_MANAGE("finance.manage"),
    STAFF_ACCESS_MANAGE("staff.access.manage"),
    LOCALIZATION_MANAGE("localization.manage"),
    DIRECTORY_MANAGE("directory.manage");

    private final String value;
    StaffPermission(String value) { this.value = value; }
    public String value() { return value; }
}
