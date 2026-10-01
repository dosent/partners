package com.simbirsoft.partners.access.infrastructure;

import java.time.Duration;
import com.simbirsoft.partners.access.application.YandexOAuthSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "partners.auth.yandex")
public record YandexOAuthProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        Duration stateTtl,
        Duration httpTimeout) implements YandexOAuthSettings {

    @Override
    public boolean configured() {
        return enabled && hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
