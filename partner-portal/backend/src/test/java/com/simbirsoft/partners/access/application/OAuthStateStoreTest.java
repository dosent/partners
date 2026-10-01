package com.simbirsoft.partners.access.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class OAuthStateStoreTest {
    @Test
    void issuedStateIsSingleUseAndUnpredictable() {
        OAuthStateStore store = store(Instant.parse("2026-10-01T10:00:00Z"));
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();

        String first = store.issue(firstSession);
        String second = store.issue(secondSession);

        assertNotEquals(first, second);
        assertTrue(store.consume(firstSession, first));
        assertFalse(store.consume(firstSession, first));
    }

    @Test
    void mismatchedStateIsRejectedAndConsumed() {
        OAuthStateStore store = store(Instant.parse("2026-10-01T10:00:00Z"));
        MockHttpSession session = new MockHttpSession();
        String expected = store.issue(session);

        assertFalse(store.consume(session, "attacker-value"));
        assertFalse(store.consume(session, expected));
    }

    @Test
    void expiredStateIsRejected() {
        Instant issuedAt = Instant.parse("2026-10-01T10:00:00Z");
        OAuthStateStore issuer = store(issuedAt);
        OAuthStateStore expiredConsumer = store(issuedAt.plus(Duration.ofMinutes(5)));
        MockHttpSession session = new MockHttpSession();
        String state = issuer.issue(session);

        assertFalse(expiredConsumer.consume(session, state));
    }

    private OAuthStateStore store(Instant instant) {
        return new OAuthStateStore(new SecureRandom(), Clock.fixed(instant, ZoneOffset.UTC), Duration.ofMinutes(5));
    }
}
