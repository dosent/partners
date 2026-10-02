package com.simbirsoft.partners.access.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "partners.auth.ldap")
public record LdapProperties(boolean enabled, String url, String usersDn, String caCertFile,
        Duration connectTimeout, Duration readTimeout) {
    public boolean configured() {
        if (!enabled || usersDn == null || usersDn.isBlank() || caCertFile == null || caCertFile.isBlank()) return false;
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            return "ldaps".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) { return false; }
    }
}
