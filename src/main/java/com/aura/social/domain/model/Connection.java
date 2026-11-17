package com.aura.social.domain.model;

import java.time.Instant;

/** A user in one of the viewer's lists (friends, requests, blocks) and since when they are there. */
public record Connection(UserSummary user, Instant since) {
}
