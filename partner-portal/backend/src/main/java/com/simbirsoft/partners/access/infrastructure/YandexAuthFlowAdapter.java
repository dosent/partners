package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.api.YandexAuthFlow;
import com.simbirsoft.partners.access.application.ExternalIdentity;
import com.simbirsoft.partners.access.application.IdentityProviderFailureException;
import com.simbirsoft.partners.access.application.OAuthStateStore;
import com.simbirsoft.partners.access.application.YandexOAuthNotConfiguredException;
import com.simbirsoft.partners.access.application.YandexOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class YandexAuthFlowAdapter implements YandexAuthFlow {
    private final YandexOAuthService oauthService;
    private final OAuthStateStore stateStore;
    private final PartnerSessionService partnerSessionService;

    public YandexAuthFlowAdapter(YandexOAuthService oauthService, OAuthStateStore stateStore,
            PartnerSessionService partnerSessionService) {
        this.oauthService = oauthService;
        this.stateStore = stateStore;
        this.partnerSessionService = partnerSessionService;
    }

    @Override
    public URI start(HttpServletRequest request) {
        String state = stateStore.issue(new HttpSessionOAuthStateSession(request.getSession(true)));
        try {
            return oauthService.authorizationUri(state);
        } catch (YandexOAuthNotConfiguredException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Yandex ID authentication is not configured");
        }
    }

    @Override
    public void callback(String code, String state, String error, HttpServletRequest request,
            HttpServletResponse response) {
        var session = request.getSession(false);
        if (session == null || !stateStore.consume(new HttpSessionOAuthStateSession(session), state)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OAuth state");
        }
        if (error != null || code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Yandex ID authorization was not completed");
        }
        ExternalIdentity identity;
        try {
            identity = oauthService.authenticate(code);
        } catch (IdentityProviderFailureException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Yandex ID authentication failed");
        }
        partnerSessionService.authenticate(identity, request, response);
    }
}
