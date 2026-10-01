package com.simbirsoft.partners.access.application;

public interface StaffAuthenticator {
    StaffIdentity authenticate(String username, String password);
}
