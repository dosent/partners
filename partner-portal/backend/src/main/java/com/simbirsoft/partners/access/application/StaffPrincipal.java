package com.simbirsoft.partners.access.application;

import java.io.Serializable;
import java.security.Principal;

public record StaffPrincipal(String username) implements Principal, Serializable {
    @Override
    public String getName() { return username; }
}
