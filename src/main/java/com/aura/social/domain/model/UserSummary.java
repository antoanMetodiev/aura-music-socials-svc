package com.aura.social.domain.model;

import java.util.UUID;

/**
 * A user as other people see them, from identity.user_profile. {@code avatarUrl} is the uploaded
 * photo, else the sign-in provider's picture, else null (the client draws initials).
 */
public record UserSummary(UUID id, String username, String displayName, String avatarUrl) {
}
