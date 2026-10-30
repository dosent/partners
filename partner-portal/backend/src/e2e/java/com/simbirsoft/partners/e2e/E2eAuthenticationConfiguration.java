package com.simbirsoft.partners.e2e;

import com.simbirsoft.partners.access.application.ExternalIdentity;
import com.simbirsoft.partners.access.application.IdentityProviderFailureException;
import com.simbirsoft.partners.access.application.StaffAccessService;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffAuthenticator;
import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.YandexIdentityProvider;
import com.simbirsoft.partners.document.application.MalwareScanner;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("e2e")
public class E2eAuthenticationConfiguration {
    public static final String STAFF_PASSWORD = "e2e-password";
    private static final Pattern STAFF_USERNAME = Pattern.compile("e2e-[a-z0-9-]+");
    private static final Pattern PARTNER_CODE = Pattern.compile("e2e-partner-[a-z0-9-]+");

    @Bean
    @Primary
    StaffAuthenticator e2eStaffAuthenticator(StaffAccessService access) {
        return (username, password) -> {
            if (username == null || password == null
                    || !STAFF_PASSWORD.equals(password)
                    || !("ivan.neshin".equals(username) || STAFF_USERNAME.matcher(username).matches())) {
                throw new StaffAuthenticationFailedException();
            }
            return new StaffIdentity(username, access.rolesFor(username));
        };
    }

    @Bean
    @Primary
    YandexIdentityProvider e2eYandexIdentityProvider() {
        return code -> {
            if (code == null || !PARTNER_CODE.matcher(code).matches()) {
                throw new IdentityProviderFailureException("Invalid e2e partner authorization code");
            }
            return new ExternalIdentity("YANDEX", code);
        };
    }

    @Bean
    @Primary
    MalwareScanner e2eMalwareScanner() {
        return content -> new String(content, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE")
                        ? MalwareScanner.Result.INFECTED : MalwareScanner.Result.CLEAN;
    }
}
