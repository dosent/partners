package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.StaffAccessService;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffAuthenticator;
import com.simbirsoft.partners.access.application.StaffDirectoryUnavailableException;
import com.simbirsoft.partners.access.application.StaffIdentity;
import javax.naming.AuthenticationException;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.Rdn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class JndiLdapStaffAuthenticator implements StaffAuthenticator {
    private static final Logger log = LoggerFactory.getLogger(JndiLdapStaffAuthenticator.class);
    private final LdapProperties properties;
    private final StaffAccessService accessService;
    private final LdapConnectionFactory connectionFactory;

    public JndiLdapStaffAuthenticator(LdapProperties properties, StaffAccessService accessService,
            LdapConnectionFactory connectionFactory) {
        this.properties = properties;
        this.accessService = accessService;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public StaffIdentity authenticate(String username, String password) {
        if (!properties.configured()) {
            throw new StaffDirectoryUnavailableException();
        }
        DirContext context;
        try {
            context = connectionFactory.open(userDn(username), password);
        } catch (AuthenticationException exception) {
            throw new StaffAuthenticationFailedException();
        } catch (NamingException exception) {
            throw new StaffDirectoryUnavailableException();
        }

        try {
            return new StaffIdentity(username.trim().toLowerCase(java.util.Locale.ROOT), accessService.rolesFor(username));
        } finally {
            try {
                context.close();
            } catch (NamingException exception) {
                log.warn("ldap_context_close_failed");
            }
        }
    }

    private String userDn(String username) {
        if (!properties.configured()) throw new StaffDirectoryUnavailableException();
        return "uid=" + Rdn.escapeValue(username.trim()) + "," + properties.usersDn();
    }
}
