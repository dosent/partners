package com.simbirsoft.partners.access.application;

import com.simbirsoft.partners.access.api.PartnerAuthenticationToken;
import com.simbirsoft.partners.access.api.PartnerPrincipal;
import com.simbirsoft.partners.access.infrastructure.YandexIdentityProviderException;
import com.simbirsoft.partners.access.infrastructure.YandexOAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class YandexOAuthService {
    private final YandexOAuthProperties properties;
    private final OAuthStateStore stateStore;
    private final YandexIdentityProvider identityProvider;
    private final SecurityContextRepository securityContextRepository;

    public YandexOAuthService(YandexOAuthProperties properties, OAuthStateStore stateStore,
            YandexIdentityProvider identityProvider, SecurityContextRepository securityContextRepository) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.identityProvider = identityProvider;
        this.securityContextRepository = securityContextRepository;
    }

    public URI authorizationUri(HttpSession session) {
        requireConfigured();
        String state = stateStore.issue(session);
        return UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", "login:info")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    public PartnerPrincipal completeLogin(String code, String returnedState, String providerError,
            HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        if (!stateStore.consume(session, returnedState)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OAuth state");
        }
        if (providerError != null || code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yandex ID authorization was not completed");
        }

        ExternalIdentity identity;
        try {
            identity = identityProvider.exchangeAuthorizationCode(code);
        } catch (YandexIdentityProviderException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Yandex ID authentication failed");
        }

        PartnerPrincipal principal = new PartnerPrincipal(identity.provider(), identity.subject());
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PartnerAuthenticationToken(principal));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return principal;
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Yandex ID authentication is not configured");
        }
    }
}
