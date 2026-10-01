package com.simbirsoft.partners.access.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/csrf")
public class CsrfTokenController {
    @GetMapping
    public CsrfTokenResponse csrfToken(CsrfToken token) {
        return new CsrfTokenResponse(token.getToken(), token.getHeaderName());
    }
}
