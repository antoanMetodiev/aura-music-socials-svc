package com.aura.social.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

/**
 * Every social endpoint belongs to a signed-in user. The bearer token is a Supabase Auth access
 * token, verified against the project's JWKS (signature, expiry, audience {@code authenticated} —
 * see application.yml); its {@code sub} is the user id. Actuator stays open for Render's health
 * checks and the gateway's keep-alive pings.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized()))
                .build();
    }

    /** Same {@code {code, message, traceId}} body as every other error (Project-Info.md §49). */
    private static AuthenticationEntryPoint unauthorized() {
        return (request, response, e) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String traceId = MDC.get(RequestIdFilter.MDC_KEY);
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Sign in to use social features\",\"traceId\":"
                    + (traceId == null ? "null" : "\"" + traceId + "\"") + "}");
        };
    }
}
