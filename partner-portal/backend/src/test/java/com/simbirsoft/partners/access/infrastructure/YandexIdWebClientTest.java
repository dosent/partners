package com.simbirsoft.partners.access.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.simbirsoft.partners.access.application.IdentityProviderFailureException;

import com.simbirsoft.partners.access.application.ExternalIdentity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class YandexIdWebClientTest {
    @Test
    void exchangesCodeAndUsesRecommendedOAuthAuthorizationHeader() {
        List<org.springframework.web.reactive.function.client.ClientRequest> requests = new ArrayList<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            String body = request.method() == HttpMethod.POST
                    ? "{\"access_token\":\"temporary-token\"}"
                    : "{\"id\":\"yandex-123\",\"login\":\"ignored\"}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        });
        YandexIdWebClient client = new YandexIdWebClient(builder, properties());

        ExternalIdentity identity = client.exchangeAuthorizationCode("one-time-code");

        assertEquals(new ExternalIdentity("YANDEX", "yandex-123"), identity);
        assertEquals("https://oauth.yandex.ru/token", requests.get(0).url().toString());
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, requests.get(0).headers().getContentType());
        assertEquals("https://login.yandex.ru/info?format=json", requests.get(1).url().toString());
        assertEquals("OAuth temporary-token", requests.get(1).headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void providerFailureDoesNotExposeResponseAsIdentity() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":\"invalid_client\"}")
                        .build()));
        YandexIdWebClient client = new YandexIdWebClient(builder, properties());

        assertThrows(IdentityProviderFailureException.class, () -> client.exchangeAuthorizationCode("bad-code"));
    }

    private YandexOAuthProperties properties() {
        return new YandexOAuthProperties(true, "client-id", "client-secret",
                "http://localhost:8080/api/v1/auth/yandex/callback", "https://oauth.yandex.ru/authorize",
                "https://oauth.yandex.ru/token", "https://login.yandex.ru/info?format=json",
                Duration.ofMinutes(5), Duration.ofSeconds(5));
    }
}
