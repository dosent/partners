package com.simbirsoft.partners.access.application;

import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class YandexOAuthService {
    private final YandexOAuthSettings settings;
    private final YandexIdentityProvider identityProvider;

    public YandexOAuthService(YandexOAuthSettings settings, YandexIdentityProvider identityProvider) {
        this.settings = settings;
        this.identityProvider = identityProvider;
    }

    public URI authorizationUri(String state) {
        if (!settings.configured()) {
            throw new YandexOAuthNotConfiguredException();
        }
        return UriComponentsBuilder.fromUriString(settings.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", settings.clientId())
                .queryParam("redirect_uri", settings.redirectUri())
                .queryParam("scope", "login:info")
                .queryParam("state", state)
                .build().encode().toUri();
    }

    public ExternalIdentity authenticate(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }
        return identityProvider.exchangeAuthorizationCode(code);
    }
}
