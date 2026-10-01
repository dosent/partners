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
import java.util.HashMap;
import java.util.Map;

class OAuthStateStoreTest {
    @Test
    void issuedStateIsSingleUseAndUnpredictable() {
        OAuthStateStore store = store(Instant.parse("2026-10-01T10:00:00Z"));
        TestSession firstSession = new TestSession();
        TestSession secondSession = new TestSession();

        String first = store.issue(firstSession);
        String second = store.issue(secondSession);

        assertNotEquals(first, second);
        assertTrue(store.consume(firstSession, first));
        assertFalse(store.consume(firstSession, first));
    }

    @Test
    void mismatchedStateIsRejectedAndConsumed() {
        OAuthStateStore store = store(Instant.parse("2026-10-01T10:00:00Z"));
        TestSession session = new TestSession();
        String expected = store.issue(session);

        assertFalse(store.consume(session, "attacker-value"));
        assertFalse(store.consume(session, expected));
    }

    @Test
    void expiredStateIsRejected() {
        Instant issuedAt = Instant.parse("2026-10-01T10:00:00Z");
        OAuthStateStore issuer = store(issuedAt);
        OAuthStateStore expiredConsumer = store(issuedAt.plus(Duration.ofMinutes(5)));
        TestSession session = new TestSession();
        String state = issuer.issue(session);

        assertFalse(expiredConsumer.consume(session, state));
    }

    private OAuthStateStore store(Instant instant) {
        return new OAuthStateStore(new SecureRandom(), Clock.fixed(instant, ZoneOffset.UTC), Duration.ofMinutes(5));
    }

    private static final class TestSession implements OAuthStateSession {
        private final Map<String, Object> attributes = new HashMap<>();
        @Override public Object getAttribute(String name) { return attributes.get(name); }
        @Override public void setAttribute(String name, Object value) { attributes.put(name, value); }
        @Override public void removeAttribute(String name) { attributes.remove(name); }
    }
}
