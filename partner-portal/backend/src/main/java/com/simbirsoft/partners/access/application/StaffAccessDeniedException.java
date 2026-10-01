package com.simbirsoft.partners.access.application;

public final class StaffAccessDeniedException extends RuntimeException {
    public StaffAccessDeniedException() {
        super("No portal role is assigned to this directory account");
    }
}
