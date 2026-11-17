package com.aura.social.adapter.persistence;

import com.aura.social.domain.model.Connection;
import com.aura.social.domain.model.Relationship;
import com.aura.social.domain.model.SocialProfile;
import com.aura.social.domain.model.UserSummary;
import com.aura.social.domain.model.UserWithRelationship;
import com.aura.social.domain.port.SocialStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres-backed social graph. Profiles are read from identity.user_profile (the front-end owns
 * that table — this service never writes it); everything else is schema {@code social}.
 */
@Repository
public class JdbcSocialStore implements SocialStore {

    private static final String USER_COLUMNS =
            "p.user_id, p.username, p.display_name, coalesce(p.avatar_url, p.picture_url) AS avatar_url";

    /** The viewer's ({@code :me}) relationship to profile {@code p}. */
    private static final String RELATIONSHIP = """
            CASE
              WHEN p.user_id = :me THEN 'SELF'
              WHEN EXISTS (SELECT 1 FROM social.blocks b WHERE b.blocker_id = :me AND b.blocked_id = p.user_id) THEN 'BLOCKED'
              WHEN EXISTS (SELECT 1 FROM social.friendships f
                           WHERE f.user_low = least(:me, p.user_id) AND f.user_high = greatest(:me, p.user_id)) THEN 'FRIENDS'
              WHEN EXISTS (SELECT 1 FROM social.friend_requests r WHERE r.requester_id = :me AND r.addressee_id = p.user_id) THEN 'REQUEST_SENT'
              WHEN EXISTS (SELECT 1 FROM social.friend_requests r WHERE r.requester_id = p.user_id AND r.addressee_id = :me) THEN 'REQUEST_RECEIVED'
              ELSE 'NONE'
            END AS relationship""";

    /** Hides everyone who blocked the viewer. */
    private static final String NOT_BLOCKED_BY_THEM =
            " NOT EXISTS (SELECT 1 FROM social.blocks b WHERE b.blocker_id = p.user_id AND b.blocked_id = :me) ";

    private final JdbcClient jdbc;

    public JdbcSocialStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Users ────────────────────────────────────────────────────────────────────────────

    @Override
    public Optional<UserWithRelationship> findUser(UUID viewer, UUID userId) {
        return jdbc.sql("SELECT " + USER_COLUMNS + ", " + RELATIONSHIP + " FROM identity.user_profile p"
                        + " WHERE p.user_id = :id AND" + NOT_BLOCKED_BY_THEM)
                .param("me", viewer)
                .param("id", userId)
                .query(JdbcSocialStore::userWithRelationship)
                .optional();
    }

    @Override
    public Optional<SocialProfile> findProfileByUsername(UUID viewer, String username) {
        String sql = "SELECT " + USER_COLUMNS + ", " + RELATIONSHIP + ","
                + " EXISTS (SELECT 1 FROM social.follows x WHERE x.follower_id = :me AND x.followee_id = p.user_id) AS following,"
                + " EXISTS (SELECT 1 FROM social.follows x WHERE x.follower_id = p.user_id AND x.followee_id = :me) AS follows_you,"
                + " (SELECT count(*) FROM social.friendships f WHERE f.user_low = p.user_id OR f.user_high = p.user_id) AS friend_count,"
                + " (SELECT count(*) FROM social.follows x WHERE x.followee_id = p.user_id) AS follower_count,"
                + " (SELECT count(*) FROM social.follows x WHERE x.follower_id = p.user_id) AS following_count"
                + " FROM identity.user_profile p"
                + " WHERE lower(p.username) = lower(:username) AND" + NOT_BLOCKED_BY_THEM;
        return jdbc.sql(sql)
                .param("me", viewer)
                .param("username", username)
                .query((rs, n) -> new SocialProfile(
                        user(rs),
                        Relationship.valueOf(rs.getString("relationship")),
                        rs.getBoolean("following"),
                        rs.getBoolean("follows_you"),
                        rs.getLong("friend_count"),
                        rs.getLong("follower_count"),
                        rs.getLong("following_count")))
                .optional();
    }

    /** Exact username first, then username prefix, then a match anywhere in the display name; the viewer is left out. */
    @Override
    public List<UserWithRelationship> searchUsers(UUID viewer, String query, int limit) {
        String lowered = query.toLowerCase();
        String needle = escapeLike(lowered);
        String sql = "SELECT " + USER_COLUMNS + ", " + RELATIONSHIP
                + " FROM identity.user_profile p"
                + " WHERE p.user_id <> :me"
                + " AND (lower(p.username) LIKE :prefix ESCAPE '\\' OR lower(p.display_name) LIKE :anywhere ESCAPE '\\')"
                + " AND" + NOT_BLOCKED_BY_THEM
                + " ORDER BY (lower(p.username) = :exact) DESC, (lower(p.username) LIKE :prefix ESCAPE '\\') DESC,"
                + " lower(p.display_name), lower(p.username)"
                + " LIMIT :limit";
        return jdbc.sql(sql)
                .param("me", viewer)
                .param("exact", lowered)
                .param("prefix", needle + "%")
                .param("anywhere", "%" + needle + "%")
                .param("limit", limit)
                .query(JdbcSocialStore::userWithRelationship)
                .list();
    }

    // ── Friendships ──────────────────────────────────────────────────────────────────────

    @Override
    public List<Connection> friends(UUID userId) {
        return connections("""
                SELECT %s, f.created_at AS since
                  FROM social.friendships f
                  JOIN identity.user_profile p
                    ON p.user_id = CASE WHEN f.user_low = :me THEN f.user_high ELSE f.user_low END
                 WHERE f.user_low = :me OR f.user_high = :me
                 ORDER BY lower(p.display_name), lower(p.username)""", userId);
    }

    @Override
    public long countFriends(UUID userId) {
        return count("SELECT count(*) FROM social.friendships WHERE user_low = :me OR user_high = :me", userId);
    }

    @Override
    public boolean addFriendship(UUID a, UUID b, Instant now) {
        return jdbc.sql("INSERT INTO social.friendships (user_low, user_high, created_at) VALUES (:low, :high, :at) ON CONFLICT DO NOTHING")
                .param("low", low(a, b))
                .param("high", high(a, b))
                .param("at", Timestamp.from(now))
                .update() > 0;
    }

    @Override
    public boolean removeFriendship(UUID a, UUID b) {
        return jdbc.sql("DELETE FROM social.friendships WHERE user_low = :low AND user_high = :high")
                .param("low", low(a, b))
                .param("high", high(a, b))
                .update() > 0;
    }

    // ── Friend requests ──────────────────────────────────────────────────────────────────

    @Override
    public List<Connection> incomingRequests(UUID userId) {
        return connections("""
                SELECT %s, r.created_at AS since
                  FROM social.friend_requests r
                  JOIN identity.user_profile p ON p.user_id = r.requester_id
                 WHERE r.addressee_id = :me
                 ORDER BY r.created_at DESC""", userId);
    }

    @Override
    public List<Connection> outgoingRequests(UUID userId) {
        return connections("""
                SELECT %s, r.created_at AS since
                  FROM social.friend_requests r
                  JOIN identity.user_profile p ON p.user_id = r.addressee_id
                 WHERE r.requester_id = :me
                 ORDER BY r.created_at DESC""", userId);
    }

    @Override
    public long countIncomingRequests(UUID userId) {
        return count("SELECT count(*) FROM social.friend_requests WHERE addressee_id = :me", userId);
    }

    @Override
    public long countOutgoingRequests(UUID userId) {
        return count("SELECT count(*) FROM social.friend_requests WHERE requester_id = :me", userId);
    }

    @Override
    public void addRequest(UUID from, UUID to, Instant now) {
        jdbc.sql("INSERT INTO social.friend_requests (requester_id, addressee_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                .params(from, to, Timestamp.from(now))
                .update();
    }

    @Override
    public boolean removeRequest(UUID from, UUID to) {
        return jdbc.sql("DELETE FROM social.friend_requests WHERE requester_id = ? AND addressee_id = ?")
                .params(from, to)
                .update() > 0;
    }

    // ── Follows ──────────────────────────────────────────────────────────────────────────

    @Override
    public void follow(UUID follower, UUID followee, Instant now) {
        jdbc.sql("INSERT INTO social.follows (follower_id, followee_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                .params(follower, followee, Timestamp.from(now))
                .update();
    }

    @Override
    public boolean unfollow(UUID follower, UUID followee) {
        return jdbc.sql("DELETE FROM social.follows WHERE follower_id = ? AND followee_id = ?")
                .params(follower, followee)
                .update() > 0;
    }

    // ── Blocks ───────────────────────────────────────────────────────────────────────────

    @Override
    public List<Connection> blocked(UUID userId) {
        return connections("""
                SELECT %s, b.created_at AS since
                  FROM social.blocks b
                  JOIN identity.user_profile p ON p.user_id = b.blocked_id
                 WHERE b.blocker_id = :me
                 ORDER BY b.created_at DESC""", userId);
    }

    @Override
    public void block(UUID blocker, UUID blocked, Instant now) {
        jdbc.sql("INSERT INTO social.blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
                .params(blocker, blocked, Timestamp.from(now))
                .update();
    }

    @Override
    public boolean unblock(UUID blocker, UUID blocked) {
        return jdbc.sql("DELETE FROM social.blocks WHERE blocker_id = ? AND blocked_id = ?")
                .params(blocker, blocked)
                .update() > 0;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────

    private List<Connection> connections(String sqlTemplate, UUID me) {
        return jdbc.sql(sqlTemplate.formatted(USER_COLUMNS))
                .param("me", me)
                .query((rs, n) -> new Connection(user(rs), rs.getTimestamp("since").toInstant()))
                .list();
    }

    private long count(String sql, UUID me) {
        return jdbc.sql(sql).param("me", me).query(Long.class).single();
    }

    private static UserWithRelationship userWithRelationship(ResultSet rs, int n) throws SQLException {
        return new UserWithRelationship(user(rs), Relationship.valueOf(rs.getString("relationship")));
    }

    private static UserSummary user(ResultSet rs) throws SQLException {
        return new UserSummary(rs.getObject("user_id", UUID.class), rs.getString("username"),
                rs.getString("display_name"), rs.getString("avatar_url"));
    }

    /*
     * The pair is ordered the way Postgres orders uuids (unsigned, byte by byte), which is what the
     * table's CHECK (user_low < user_high) and least()/greatest() use. UUID.compareTo compares
     * signed longs and disagrees for ids starting with 8–f, so compare the canonical lowercase hex.
     */
    private static UUID low(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? a : b;
    }

    private static UUID high(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? b : a;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
