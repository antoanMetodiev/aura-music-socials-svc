package com.aura.social.domain.model;

/**
 * Where the viewer stands with another user, from the viewer's side. Someone who blocked the
 * viewer is never shown to them at all, so there is no "blocked by" value.
 */
public enum Relationship {
    SELF,
    NONE,
    FRIENDS,
    /** The viewer asked; waiting on the other user. */
    REQUEST_SENT,
    /** The other user asked; the viewer can accept or decline. */
    REQUEST_RECEIVED,
    /** The viewer blocked them. */
    BLOCKED
}
