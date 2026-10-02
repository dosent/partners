package com.simbirsoft.partners.access.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.shared.api.ApiError;
import com.simbirsoft.partners.shared.api.TraceIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableConfigurationProperties({YandexOAuthProperties.class, LdapProperties.class})
public class SecurityConfiguration {
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
            ObjectMapper objectMapper, com.simbirsoft.partners.access.application.StaffAccessService staffAccess)
            throws Exception {
        http
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/yandex/start", "/api/v1/auth/yandex/callback",
                                "/api/v1/auth/staff/login", "/api/v1/auth/csrf",
                                "/api/v1/public/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/yandex/session").hasRole("PARTNER")
                        .requestMatchers("/api/v1/auth/staff/session", "/api/v1/auth/staff/logout").hasRole("STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/v1/partner-applications").hasRole("PARTNER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/partner-applications/me").hasRole("PARTNER")
                        .requestMatchers("/api/v1/partner-applications/me").hasRole("PARTNER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/admin/partner-applications/*/approve",
                                "/api/v1/admin/partner-applications/*/reject",
                                "/api/v1/admin/partner-applications/*/request-changes")
                        .hasAuthority("partner.application.decide")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/partner-applications/**")
                        .hasAuthority("partner.application.read")
                        .requestMatchers("/api/v1/admin/partner-applications/**")
                        .hasAuthority("partner.application.read")
                        .requestMatchers("/api/v1/admin/registration-content/**").hasAuthority("localization.manage")
                        .requestMatchers("/api/v1/admin/staff-access/**").hasAuthority("staff.access.manage")
                        .anyRequest().authenticated())
                .addFilterBefore(new StaffAccessRefreshFilter(staffAccess, objectMapper), AuthorizationFilter.class)
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> writeSecurityError(
                                request, response, objectMapper, 401, "UNAUTHORIZED", "Authentication required"))
                        .accessDeniedHandler((request, response, failure) -> writeSecurityError(
                                request, response, objectMapper, 403, "FORBIDDEN", "Insufficient permission")))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private static void writeSecurityError(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper,
            int status, String code, String message) throws java.io.IOException {
        String traceId = response.getHeader(TraceIdFilter.HEADER);
        if (traceId == null) traceId = UUID.randomUUID().toString();
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader(TraceIdFilter.HEADER, traceId);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), status, code, message,
                traceId, List.of()));
    }
}
