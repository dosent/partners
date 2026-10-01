package com.simbirsoft.partners.access.application;

public final class StaffAuthenticationFailedException extends RuntimeException {
    public StaffAuthenticationFailedException() {
        super("Staff authentication failed");
    }
}
