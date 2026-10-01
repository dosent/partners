package com.simbirsoft.partners.access.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.shared.api.ApiError;
import com.simbirsoft.partners.shared.api.TraceIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableConfigurationProperties(YandexOAuthProperties.class)
public class SecurityConfiguration {
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
            ObjectMapper objectMapper) throws Exception {
        http
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/yandex/start", "/api/v1/auth/yandex/callback",
                                "/api/v1/public/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, failure) -> {
                    String traceId = response.getHeader(TraceIdFilter.HEADER);
                    if (traceId == null) {
                        traceId = UUID.randomUUID().toString();
                    }
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.setHeader(TraceIdFilter.HEADER, traceId);
                    ApiError error = new ApiError(Instant.now(), 401, "UNAUTHORIZED", "Authentication required",
                            traceId, List.of());
                    objectMapper.writeValue(response.getOutputStream(), error);
                }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
