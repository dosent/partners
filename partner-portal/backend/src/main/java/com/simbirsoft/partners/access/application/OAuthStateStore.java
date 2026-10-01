package com.simbirsoft.partners.access.application;

import jakarta.servlet.http.HttpSession;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class OAuthStateStore {
    private static final String SESSION_ATTRIBUTE = OAuthStateStore.class.getName() + ".pendingState";
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration lifetime;

    @Autowired
    public OAuthStateStore(com.simbirsoft.partners.access.infrastructure.YandexOAuthProperties properties) {
        this(new SecureRandom(), Clock.systemUTC(), properties.stateTtl());
    }

    OAuthStateStore(SecureRandom secureRandom, Clock clock, Duration lifetime) {
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    public String issue(HttpSession session) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTRIBUTE, new PendingState(state, clock.instant().plus(lifetime)));
        return state;
    }

    public boolean consume(HttpSession session, String returnedState) {
        if (session == null) {
            return false;
        }
        Object stored = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        if (!(stored instanceof PendingState pending) || returnedState == null
                || !clock.instant().isBefore(pending.expiresAt())) {
            return false;
        }
        return MessageDigest.isEqual(pending.value().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                returnedState.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record PendingState(String value, Instant expiresAt) implements java.io.Serializable {
    }
}
