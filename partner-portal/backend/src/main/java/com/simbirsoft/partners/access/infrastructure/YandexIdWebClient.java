package com.simbirsoft.partners.access.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.simbirsoft.partners.access.application.ExternalIdentity;
import com.simbirsoft.partners.access.application.YandexIdentityProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public final class YandexIdWebClient implements YandexIdentityProvider {
    private static final String PROVIDER = "YANDEX";
    private final WebClient webClient;
    private final YandexOAuthProperties properties;

    public YandexIdWebClient(WebClient.Builder builder, YandexOAuthProperties properties) {
        this.webClient = builder.build();
        this.properties = properties;
    }

    @Override
    public ExternalIdentity exchangeAuthorizationCode(String code) {
        try {
            YandexToken token = exchangeCode(code);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new YandexIdentityProviderException("Yandex ID returned no access token");
            }
            YandexUserInfo user = fetchUser(token.accessToken());
            if (user == null || user.id() == null || user.id().isBlank()) {
                throw new YandexIdentityProviderException("Yandex ID returned no user identifier");
            }
            return new ExternalIdentity(PROVIDER, user.id());
        } catch (YandexIdentityProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new YandexIdentityProviderException("Yandex ID authentication failed", exception);
        }
    }

    private YandexToken exchangeCode(String code) {
        return webClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("code", code)
                        .with("client_id", properties.clientId())
                        .with("client_secret", properties.clientSecret())
                        .with("redirect_uri", properties.redirectUri()))
                .retrieve()
                .bodyToMono(YandexToken.class)
                .block(properties.httpTimeout());
    }

    private YandexUserInfo fetchUser(String accessToken) {
        return webClient.get()
                .uri(properties.userInfoUri())
                .headers(headers -> headers.set("Authorization", "OAuth " + accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(YandexUserInfo.class)
                .block(properties.httpTimeout());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YandexToken(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YandexUserInfo(@JsonProperty("id") String id) {
    }
}
