package com.aura.social.domain.model;

/** Someone's profile header as the viewer sees it: who they are, how the two relate, the counts. */
public record SocialProfile(
        UserSummary user,
        Relationship relationship,
        boolean following,
        boolean followsYou,
        long friendCount,
        long followerCount,
        long followingCount) {
}
