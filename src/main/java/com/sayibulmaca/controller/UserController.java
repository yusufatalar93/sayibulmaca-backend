package com.sayibulmaca.controller;

import com.sayibulmaca.dto.UserProfileResponse;
import com.sayibulmaca.dto.UsernameRequest;
import com.sayibulmaca.game.LeagueUtil;
import com.sayibulmaca.model.Rating;
import com.sayibulmaca.model.User;
import com.sayibulmaca.security.UserPrincipal;
import com.sayibulmaca.service.MatchService;
import com.sayibulmaca.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserService userService;
    private final MatchService matchService;

    public UserController(UserService userService, MatchService matchService) {
        this.userService = userService;
        this.matchService = matchService;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getUserById(principal.getUser().getId());
        List<Rating> ratings = userService.getUserRatings(user.getId());

        List<UserProfileResponse.RatingResponse> ratingResponses = ratings.stream().map(r ->
                UserProfileResponse.RatingResponse.builder()
                        .digitMode(r.getId().getDigitMode())
                        .rating(r.getRating())
                        .league(LeagueUtil.getLeague(r.getRating()))
                        .tier(LeagueUtil.getTier(r.getRating()))
                        .gamesPlayed(r.getGamesPlayed())
                        .wins(r.getWins())
                        .losses(r.getLosses())
                        .draws(r.getDraws())
                        .progressToNextTier(LeagueUtil.getProgressToNextTier(r.getRating()))
                        .build()
        ).collect(Collectors.toList());

        UUID userId = user.getId();
        UUID activeMatchId = matchService.getActiveMatchId(userId).orElse(null);
        String activeMatchPhase = matchService.getActiveMatchPhase(userId).orElse(null);

        UserProfileResponse response = UserProfileResponse.builder()
                .id(userId)
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .ratings(ratingResponses)
                .activeMatchId(activeMatchId)
                .activeMatchPhase(activeMatchPhase)
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/username")
    public ResponseEntity<UserProfileResponse> setUsername(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UsernameRequest request) {
        
        userService.setUsername(principal.getUser().getId(), request.getUsername());
        return getMe(principal);
    }
}
