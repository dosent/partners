package com.simbirsoft.partners.access.application;

public final class IdentityProviderFailureException extends RuntimeException {
    public IdentityProviderFailureException(String message) { super(message); }
    public IdentityProviderFailureException(String message, Throwable cause) { super(message, cause); }
}
