package com.simbirsoft.partners.access.infrastructure;

import java.util.Hashtable;
import jakarta.annotation.PostConstruct;
import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.springframework.stereotype.Component;

@Component
public final class JndiLdapConnectionFactory implements LdapConnectionFactory {
    private final LdapProperties properties;

    public JndiLdapConnectionFactory(LdapProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void configureCaTrust() {
        if (properties.enabled()) {
            if (!properties.configured()) throw new IllegalStateException("LDAP is enabled but settings are incomplete");
            LdapCaSocketFactory.configure(properties.caCertFile());
        }
    }

    @Override
    public DirContext open(String principal, String password) throws javax.naming.NamingException {
        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, properties.url());
        environment.put(Context.SECURITY_AUTHENTICATION, "simple");
        environment.put(Context.SECURITY_PRINCIPAL, principal);
        environment.put(Context.SECURITY_CREDENTIALS, password);
        environment.put(Context.REFERRAL, "ignore");
        environment.put("java.naming.ldap.factory.socket", LdapCaSocketFactory.class.getName());
        environment.put("com.sun.jndi.ldap.connect.timeout", timeoutMillis(properties.connectTimeout()));
        environment.put("com.sun.jndi.ldap.read.timeout", timeoutMillis(properties.readTimeout()));
        return new InitialDirContext(environment);
    }

    private String timeoutMillis(java.time.Duration duration) {
        java.time.Duration effective = duration == null || duration.isNegative() || duration.isZero()
                ? java.time.Duration.ofSeconds(3) : duration;
        return Long.toString(effective.toMillis());
    }
}
