package com.simbirsoft.partners.access.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;

/** HTTP-facing operations used by the Yandex ID endpoints. */
public interface YandexAuthFlow {
    URI start(HttpServletRequest request);
    void callback(String code, String state, String error, HttpServletRequest request,
            HttpServletResponse response);
}
