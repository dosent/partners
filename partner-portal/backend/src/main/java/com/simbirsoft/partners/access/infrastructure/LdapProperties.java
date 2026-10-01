package com.simbirsoft.partners.access.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "partners.auth.ldap")
public record LdapProperties(boolean enabled, String url, String userDnPattern, String groupSearchBase,
        String groupSearchFilter, Duration connectTimeout, Duration readTimeout,
        Map<String, String> roleGroups) {
    public LdapProperties {
        roleGroups = roleGroups == null ? Map.of() : Map.copyOf(roleGroups);
    }

    public boolean configured() {
        int userPlaceholder = userDnPattern == null ? -1 : userDnPattern.indexOf("{0}");
        boolean validUserDnPattern = userPlaceholder >= 0
                && userDnPattern.indexOf("{0}", userPlaceholder + 3) < 0;
        return enabled && secureUrl(url) && validUserDnPattern
                && hasText(groupSearchBase) && hasText(groupSearchFilter) && groupSearchFilter.contains("{0}")
                && roleGroups.values().stream().anyMatch(LdapProperties::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean secureUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return "ldaps".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
