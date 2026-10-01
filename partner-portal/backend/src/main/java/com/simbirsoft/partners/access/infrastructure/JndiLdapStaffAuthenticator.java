package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.LdapGroupRoleMapper;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffAuthenticator;
import com.simbirsoft.partners.access.application.StaffDirectoryUnavailableException;
import com.simbirsoft.partners.access.application.StaffIdentity;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.naming.AuthenticationException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Rdn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class JndiLdapStaffAuthenticator implements StaffAuthenticator {
    private static final Logger log = LoggerFactory.getLogger(JndiLdapStaffAuthenticator.class);
    private final LdapProperties properties;
    private final LdapGroupRoleMapper roleMapper;
    private final LdapConnectionFactory connectionFactory;

    public JndiLdapStaffAuthenticator(LdapProperties properties, LdapGroupRoleMapper roleMapper,
            LdapConnectionFactory connectionFactory) {
        this.properties = properties;
        this.roleMapper = roleMapper;
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
            Set<String> groups = findGroups(context, userDn(username));
            return new StaffIdentity(username, roleMapper.map(groups));
        } catch (NamingException exception) {
            throw new StaffDirectoryUnavailableException();
        } finally {
            try {
                context.close();
            } catch (NamingException exception) {
                log.warn("ldap_context_close_failed");
            }
        }
    }

    private Set<String> findGroups(DirContext context, String userDn) throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(new String[0]);
        Set<String> groups = new LinkedHashSet<>();
        NamingEnumeration<SearchResult> results = context.search(properties.groupSearchBase(),
                properties.groupSearchFilter(), new Object[] {userDn}, controls);
        try {
            while (results.hasMore()) {
                groups.add(results.next().getNameInNamespace());
            }
        } finally {
            results.close();
        }
        return groups;
    }

    private String userDn(String username) {
        String pattern = properties.userDnPattern();
        int placeholder = pattern.indexOf("{0}");
        if (placeholder < 0 || pattern.indexOf("{0}", placeholder + 3) >= 0) {
            throw new StaffDirectoryUnavailableException();
        }
        return pattern.substring(0, placeholder) + Rdn.escapeValue(username) + pattern.substring(placeholder + 3);
    }
}
