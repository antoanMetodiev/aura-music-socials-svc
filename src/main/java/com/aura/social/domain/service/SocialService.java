package com.aura.social.domain.service;

import com.aura.social.config.SocialProperties;
import com.aura.social.domain.model.Connection;
import com.aura.social.domain.model.Relationship;
import com.aura.social.domain.model.SocialProfile;
import com.aura.social.domain.model.UserWithRelationship;
import com.aura.social.domain.port.SocialStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The social graph (Project-Info.md §11, §43): friend requests → friendships, one-way follows and
 * blocks. Every write is idempotent — repeating an action returns the state it already led to — so
 * a double click or a retried request never errors.
 */
@Service
public class SocialService {

    private static final int MAX_QUERY_LENGTH = 50;

    private final SocialStore store;
    private final SocialProperties properties;
    private final Clock clock;

    public SocialService(SocialStore store, SocialProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    public record Summary(long friends, long incomingRequests) {
    }

    // ── Reads ────────────────────────────────────────────────────────────────────────────

    public SocialProfile profile(UUID viewer, String username) {
        return store.findProfileByUsername(viewer, username).orElseThrow(SocialExceptions.UserNotFound::new);
    }

    public List<UserWithRelationship> search(UUID viewer, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.startsWith("@")) query = query.substring(1);
        if (query.isEmpty()) return List.of();
        if (query.length() > MAX_QUERY_LENGTH) query = query.substring(0, MAX_QUERY_LENGTH);
        return store.searchUsers(viewer, query, properties.maxSearchResults());
    }

    public List<Connection> friends(UUID userId) {
        return store.friends(userId);
    }

    public List<Connection> incomingRequests(UUID userId) {
        return store.incomingRequests(userId);
    }

    public List<Connection> outgoingRequests(UUID userId) {
        return store.outgoingRequests(userId);
    }

    public List<Connection> blocked(UUID userId) {
        return store.blocked(userId);
    }

    public Summary summary(UUID userId) {
        return new Summary(store.countFriends(userId), store.countIncomingRequests(userId));
    }

    // ── Friends ──────────────────────────────────────────────────────────────────────────

    /** Asking someone who already asked you is the same as accepting their request. */
    @Transactional
    public Relationship sendRequest(UUID me, UUID target) {
        UserWithRelationship other = existingOther(me, target);
        return switch (other.relationship()) {
            case FRIENDS, REQUEST_SENT -> other.relationship();
            case REQUEST_RECEIVED -> accept(me, target);
            case BLOCKED -> throw new SocialExceptions.Conflict("USER_BLOCKED", "Unblock this user first");
            case SELF -> throw new IllegalArgumentException("You cannot befriend yourself");
            case NONE -> {
                if (store.countOutgoingRequests(me) >= properties.maxPendingOutgoing()) {
                    throw new SocialExceptions.LimitExceeded("Too many unanswered friend requests");
                }
                requireRoomForFriend(me);
                store.addRequest(me, target, Instant.now(clock));
                yield Relationship.REQUEST_SENT;
            }
        };
    }

    @Transactional
    public Relationship accept(UUID me, UUID requester) {
        boolean hadRequest = store.removeRequest(requester, me);
        // Both sides may have asked at the same moment; the other half goes too.
        store.removeRequest(me, requester);
        if (!hadRequest) {
            if (existingOther(me, requester).relationship() == Relationship.FRIENDS) return Relationship.FRIENDS;
            throw new SocialExceptions.Conflict("NO_REQUEST", "This user has not sent you a friend request");
        }
        requireRoomForFriend(me);
        store.addFriendship(me, requester, Instant.now(clock));
        return Relationship.FRIENDS;
    }

    /** Declines a request from them or cancels one to them — whichever exists. */
    @Transactional
    public void removeRequest(UUID me, UUID other) {
        store.removeRequest(me, other);
        store.removeRequest(other, me);
    }

    @Transactional
    public void unfriend(UUID me, UUID other) {
        store.removeFriendship(me, other);
    }

    // ── Follows ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void follow(UUID me, UUID target) {
        Relationship relationship = existingOther(me, target).relationship();
        if (relationship == Relationship.SELF) throw new IllegalArgumentException("You cannot follow yourself");
        if (relationship == Relationship.BLOCKED) throw new SocialExceptions.Conflict("USER_BLOCKED", "Unblock this user first");
        store.follow(me, target, Instant.now(clock));
    }

    @Transactional
    public void unfollow(UUID me, UUID target) {
        store.unfollow(me, target);
    }

    // ── Blocks ───────────────────────────────────────────────────────────────────────────

    /** Cuts every tie both ways — friendship, requests, follows — then blocks. */
    @Transactional
    public void block(UUID me, UUID target) {
        if (existingOther(me, target).relationship() == Relationship.SELF) {
            throw new IllegalArgumentException("You cannot block yourself");
        }
        store.removeFriendship(me, target);
        store.removeRequest(me, target);
        store.removeRequest(target, me);
        store.unfollow(me, target);
        store.unfollow(target, me);
        store.block(me, target, Instant.now(clock));
    }

    @Transactional
    public void unblock(UUID me, UUID target) {
        store.unblock(me, target);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────

    private UserWithRelationship existingOther(UUID me, UUID other) {
        return store.findUser(me, other).orElseThrow(SocialExceptions.UserNotFound::new);
    }

    private void requireRoomForFriend(UUID me) {
        if (store.countFriends(me) >= properties.maxFriends()) {
            throw new SocialExceptions.LimitExceeded("Friend limit reached");
        }
    }
}
