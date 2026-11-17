package com.aura.social.domain.port;

import com.aura.social.domain.model.Connection;
import com.aura.social.domain.model.SocialProfile;
import com.aura.social.domain.model.UserWithRelationship;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the social graph. Lookups of other users never return someone who blocked the
 * viewer — to the viewer, that account does not exist.
 */
public interface SocialStore {

    // ── Users ────────────────────────────────────────────────────────────────────────────
    Optional<UserWithRelationship> findUser(UUID viewer, UUID userId);

    Optional<SocialProfile> findProfileByUsername(UUID viewer, String username);

    List<UserWithRelationship> searchUsers(UUID viewer, String query, int limit);

    // ── Friendships ──────────────────────────────────────────────────────────────────────
    List<Connection> friends(UUID userId);

    long countFriends(UUID userId);

    /** False when the pair already were friends. */
    boolean addFriendship(UUID a, UUID b, Instant now);

    boolean removeFriendship(UUID a, UUID b);

    // ── Friend requests ──────────────────────────────────────────────────────────────────
    List<Connection> incomingRequests(UUID userId);

    List<Connection> outgoingRequests(UUID userId);

    long countIncomingRequests(UUID userId);

    long countOutgoingRequests(UUID userId);

    void addRequest(UUID from, UUID to, Instant now);

    boolean removeRequest(UUID from, UUID to);

    // ── Follows ──────────────────────────────────────────────────────────────────────────
    void follow(UUID follower, UUID followee, Instant now);

    boolean unfollow(UUID follower, UUID followee);

    // ── Blocks ───────────────────────────────────────────────────────────────────────────
    List<Connection> blocked(UUID userId);

    void block(UUID blocker, UUID blocked, Instant now);

    boolean unblock(UUID blocker, UUID blocked);
}
