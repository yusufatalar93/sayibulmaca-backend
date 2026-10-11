package com.sayibulmaca.controller;

import com.sayibulmaca.dto.MatchDetailResponse;
import com.sayibulmaca.dto.MatchSummaryResponse;
import com.sayibulmaca.game.MatchResult;
import com.sayibulmaca.model.Match;
import com.sayibulmaca.repository.MatchRepository;
import com.sayibulmaca.security.UserPrincipal;
import com.sayibulmaca.service.MatchPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatchHistoryController {

    private final MatchRepository matchRepository;
    private final MatchPersistenceService matchPersistenceService;

    @GetMapping("/me/matches")
    public ResponseEntity<List<MatchSummaryResponse>> getMyMatches(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page) {

        UUID userId = principal.getUser().getId();
        Page<Match> matches = matchRepository.findFinishedByPlayer(userId, PageRequest.of(page, 20));

        List<MatchSummaryResponse> responses = matches.getContent().stream()
                .map(m -> toSummary(m, userId))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/matches/{id}")
    public ResponseEntity<MatchDetailResponse> getMatchDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        UUID userId = principal.getUser().getId();
        MatchDetailResponse detail = matchPersistenceService.getMatchDetail(id, userId);
        return ResponseEntity.ok(detail);
    }

    private MatchSummaryResponse toSummary(Match match, UUID userId) {
        boolean isPlayerA = match.getPlayerA().getId().equals(userId);
        String opponentUsername = isPlayerA
                ? safeUsername(match.getPlayerB())
                : safeUsername(match.getPlayerA());

        String myResult = "draw";
        if (match.getResult() != null) {
            if (match.getResult() == MatchResult.DRAW) myResult = "draw";
            else if (isPlayerA) myResult = match.getResult() == MatchResult.A_WON ? "won" : "lost";
            else myResult = match.getResult() == MatchResult.B_WON ? "won" : "lost";
        }

        Integer myRatingChange = isPlayerA ? match.getRatingChangeA() : match.getRatingChangeB();

        return MatchSummaryResponse.builder()
                .matchId(match.getId())
                .opponentUsername(opponentUsername)
                .myResult(myResult)
                .myRatingChange(myRatingChange)
                .digitMode(match.getDigitMode())
                .timeControl(match.getTimeControl().name())
                .finishedAt(match.getFinishedAt())
                .build();
    }

    private String safeUsername(com.sayibulmaca.model.User user) {
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }
}
