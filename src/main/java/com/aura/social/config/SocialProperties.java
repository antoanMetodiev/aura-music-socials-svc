package com.aura.social.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code aura.social.*} — per-user limits. */
@ConfigurationProperties("aura.social")
public record SocialProperties(int maxFriends, int maxPendingOutgoing, int maxSearchResults) {
}
