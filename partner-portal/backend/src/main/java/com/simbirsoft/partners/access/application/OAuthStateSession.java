package com.simbirsoft.partners.access.application;

public interface OAuthStateSession {
    Object getAttribute(String name);
    void setAttribute(String name, Object value);
    void removeAttribute(String name);
}
