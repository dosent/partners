package com.simbirsoft.partners.access.application;

public final class StaffDirectoryUnavailableException extends RuntimeException {
    public StaffDirectoryUnavailableException() {
        super("Staff directory is unavailable");
    }
}
