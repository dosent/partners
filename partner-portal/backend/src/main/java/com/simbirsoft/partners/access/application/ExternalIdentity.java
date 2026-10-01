package com.simbirsoft.partners.access.application;

public record ExternalIdentity(String provider, String subject) {
    public ExternalIdentity {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
    }
}
