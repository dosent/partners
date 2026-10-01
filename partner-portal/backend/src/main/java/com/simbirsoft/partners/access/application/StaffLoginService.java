package com.simbirsoft.partners.access.application;

import org.springframework.stereotype.Service;

@Service
public final class StaffLoginService {
    private final StaffAuthenticator authenticator;

    public StaffLoginService(StaffAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public StaffIdentity authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new StaffAuthenticationFailedException();
        }
        return authenticator.authenticate(username.trim(), password);
    }
}
