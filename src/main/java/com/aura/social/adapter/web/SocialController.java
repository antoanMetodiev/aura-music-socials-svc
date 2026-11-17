package com.aura.social.adapter.web;

import com.aura.social.domain.model.Connection;
import com.aura.social.domain.model.Relationship;
import com.aura.social.domain.model.SocialProfile;
import com.aura.social.domain.model.UserWithRelationship;
import com.aura.social.domain.service.SocialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/social/**} — the signed-in user's friends, friend requests, follows and blocks,
 * plus finding other people. The acting user always comes from the token, never from the body.
 */
@RestController
@RequestMapping("/api/v1/social")
public class SocialController {

    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    public record FriendRequestBody(@NotNull UUID userId) {
    }

    public record RelationshipResponse(Relationship relationship) {
    }

    // ── People ───────────────────────────────────────────────────────────────────────────

    @GetMapping("/users/search")
    public List<UserWithRelationship> search(@AuthenticationPrincipal Jwt jwt, @RequestParam("q") String query) {
        return service.search(CurrentUser.id(jwt), query);
    }

    @GetMapping("/users/by-username/{username}")
    public SocialProfile profile(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        return service.profile(CurrentUser.id(jwt), username);
    }

    /** Badge counts for the navigation. */
    @GetMapping("/me/summary")
    public SocialService.Summary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(CurrentUser.id(jwt));
    }

    // ── Friends ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/friends")
    public List<Connection> friends(@AuthenticationPrincipal Jwt jwt) {
        return service.friends(CurrentUser.id(jwt));
    }

    @DeleteMapping("/friends/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfriend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        service.unfriend(CurrentUser.id(jwt), userId);
    }

    @GetMapping("/friend-requests/incoming")
    public List<Connection> incoming(@AuthenticationPrincipal Jwt jwt) {
        return service.incomingRequests(CurrentUser.id(jwt));
    }

    @GetMapping("/friend-requests/outgoing")
    public List<Connection> outgoing(@AuthenticationPrincipal Jwt jwt) {
        return service.outgoingRequests(CurrentUser.id(jwt));
    }

    /** {@code REQUEST_SENT}, or {@code FRIENDS} when they had already asked you. */
    @PostMapping("/friend-requests")
    public RelationshipResponse sendRequest(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FriendRequestBody body) {
        return new RelationshipResponse(service.sendRequest(CurrentUser.id(jwt), body.userId()));
    }

    @PostMapping("/friend-requests/{userId}/accept")
    public RelationshipResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return new RelationshipResponse(service.accept(CurrentUser.id(jwt), userId));
    }

    /** Declines their request or cancels yours. */
    @DeleteMapping("/friend-requests/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        service.removeRequest(CurrentUser.id(jwt), userId);
    }

    // ── Follows ──────────────────────────────────────────────────────────────────────────

    @PutMapping("/follows/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        service.follow(CurrentUser.id(jwt), userId);
    }

    @DeleteMapping("/follows/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        service.unfollow(CurrentUser.id(jwt), userId);
    }

    // ── Blocks ───────────────────────────────────────────────────────────────────────────

    @GetMapping("/blocks")
    public List<Connection> blocked(@AuthenticationPrincipal Jwt jwt) {
        return service.blocked(CurrentUser.id(jwt));
    }

    @PutMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        service.block(CurrentUser.id(jwt), userId);
    }

    @DeleteMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        service.unblock(CurrentUser.id(jwt), userId);
    }
}
