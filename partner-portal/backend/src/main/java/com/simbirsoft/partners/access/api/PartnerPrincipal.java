package com.simbirsoft.partners.access.api;

import java.io.Serializable;
import java.security.Principal;

public record PartnerPrincipal(String provider, String subject) implements Principal, Serializable {
    @Override
    public String getName() {
        return provider + ":" + subject;
    }
}
