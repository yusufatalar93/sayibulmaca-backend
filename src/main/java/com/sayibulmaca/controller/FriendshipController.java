package com.sayibulmaca.controller;

import com.sayibulmaca.dto.FriendResponse;
import com.sayibulmaca.security.UserPrincipal;
import com.sayibulmaca.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @GetMapping
    public ResponseEntity<List<FriendResponse>> getFriends(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendshipService.getFriends(principal.getUser().getId()));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendResponse>> getPendingRequests(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(friendshipService.getPendingRequests(principal.getUser().getId()));
    }

    @PostMapping("/request")
    public ResponseEntity<FriendResponse> sendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String usernameOrEmail = body.get("usernameOrEmail");
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(friendshipService.sendRequest(principal.getUser().getId(), usernameOrEmail.trim()));
    }

    @PostMapping("/{friendshipId}/accept")
    public ResponseEntity<FriendResponse> acceptRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID friendshipId) {
        return ResponseEntity.ok(friendshipService.acceptRequest(principal.getUser().getId(), friendshipId));
    }

    @PostMapping("/{friendshipId}/decline")
    public ResponseEntity<Void> declineRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID friendshipId) {
        friendshipService.declineRequest(principal.getUser().getId(), friendshipId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID friendshipId) {
        friendshipService.removeFriend(principal.getUser().getId(), friendshipId);
        return ResponseEntity.ok().build();
    }
}
