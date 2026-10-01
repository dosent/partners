package com.simbirsoft.partners.access.application;

import java.time.Duration;

public interface YandexOAuthSettings {
    boolean configured();
    String clientId();
    String clientSecret();
    String redirectUri();
    String authorizationUri();
    String tokenUri();
    String userInfoUri();
    Duration stateTtl();
    Duration httpTimeout();
}
