package com.simbirsoft.partners.access.infrastructure;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

@FunctionalInterface
public interface LdapConnectionFactory {
    DirContext open(String principal, String password) throws NamingException;
}
