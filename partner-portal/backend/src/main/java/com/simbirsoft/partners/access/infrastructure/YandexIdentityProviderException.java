package com.simbirsoft.partners.access.infrastructure;

public final class YandexIdentityProviderException extends RuntimeException {
    public YandexIdentityProviderException(String message) {
        super(message);
    }

    public YandexIdentityProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
