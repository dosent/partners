package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.application.OAuthStateSession;
import jakarta.servlet.http.HttpSession;

public final class HttpSessionOAuthStateSession implements OAuthStateSession {
    private final HttpSession session;

    public HttpSessionOAuthStateSession(HttpSession session) { this.session = session; }
    @Override public Object getAttribute(String name) { return session.getAttribute(name); }
    @Override public void setAttribute(String name, Object value) { session.setAttribute(name, value); }
    @Override public void removeAttribute(String name) { session.removeAttribute(name); }
}
