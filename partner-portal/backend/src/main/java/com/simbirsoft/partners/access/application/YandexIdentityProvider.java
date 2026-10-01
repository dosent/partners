package com.simbirsoft.partners.access.application;

public interface YandexIdentityProvider {
    ExternalIdentity exchangeAuthorizationCode(String code);
}
