-- Identity & Social Service (Project-Info.md §11). Owns schema `social`.
-- Users are Supabase `auth.users.id`; their username / display name / photo stay in
-- identity.user_profile (written by the front-end at sign-in), which this service only reads.

-- A friendship is symmetric, so it is stored once with the pair ordered (user_low < user_high).
CREATE TABLE friendships (
    user_low    UUID        NOT NULL,
    user_high   UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_low, user_high),
    CHECK (user_low < user_high)
);
CREATE INDEX friendships_high_idx ON friendships (user_high);

-- Only pending requests live here: accepting turns the row into a friendship, declining or
-- cancelling deletes it.
CREATE TABLE friend_requests (
    requester_id  UUID        NOT NULL,
    addressee_id  UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (requester_id, addressee_id),
    CHECK (requester_id <> addressee_id)
);
CREATE INDEX friend_requests_addressee_idx ON friend_requests (addressee_id, created_at DESC);

-- One-way: follow someone to see their public activity without being friends.
CREATE TABLE follows (
    follower_id  UUID        NOT NULL,
    followee_id  UUID        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (follower_id, followee_id),
    CHECK (follower_id <> followee_id)
);
CREATE INDEX follows_followee_idx ON follows (followee_id);

-- Blocking removes any friendship, request and follow between the two, and stops new ones.
CREATE TABLE blocks (
    blocker_id  UUID        NOT NULL,
    blocked_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX blocks_blocked_idx ON blocks (blocked_id);
