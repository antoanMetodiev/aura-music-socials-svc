package com.aura.social.adapter.web;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** The Supabase user id is the access token's {@code sub}. The token is already verified by then. */
final class CurrentUser {

    private CurrentUser() {
    }

    static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
