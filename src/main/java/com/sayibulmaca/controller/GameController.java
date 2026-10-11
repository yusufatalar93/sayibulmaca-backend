package com.sayibulmaca.controller;

import com.sayibulmaca.game.TimeControl;
import com.sayibulmaca.security.UserPrincipal;
import com.sayibulmaca.service.ChallengeService;
import com.sayibulmaca.service.MatchService;
import com.sayibulmaca.service.MatchmakingQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final MatchService matchService;
    private final MatchmakingQueue matchmakingQueue;
    private final ChallengeService challengeService;

    @MessageMapping("/queue.join")
    public void joinQueue(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        int digitMode = ((Number) payload.get("digitMode")).intValue();
        String tcStr = (String) payload.get("timeControl");
        TimeControl timeControl;
        try {
            timeControl = TimeControl.valueOf(tcStr.toUpperCase());
        } catch (Exception e) {
            return;
        }

        matchmakingQueue.addToQueue(userId, digitMode, timeControl);
    }

    @MessageMapping("/queue.leave")
    public void leaveQueue(Principal principal) {
        UUID userId = getUserId(principal);
        if (userId == null) return;
        matchmakingQueue.removeFromQueue(userId);
    }

    @MessageMapping("/match.setSecret")
    public void setSecret(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        UUID matchId = UUID.fromString((String) payload.get("matchId"));

        Boolean auto = (Boolean) payload.get("auto");
        if (Boolean.TRUE.equals(auto)) {
            matchService.autoAssignSecret(userId, matchId);
        } else {
            String secret = (String) payload.get("secret");
            matchService.setSecret(userId, matchId, secret);
        }
    }

    @MessageMapping("/match.guess")
    public void makeGuess(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        UUID matchId = UUID.fromString((String) payload.get("matchId"));
        String value = (String) payload.get("value");
        matchService.makeGuess(userId, matchId, value);
    }

    @MessageMapping("/match.resign")
    public void resign(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        UUID matchId = UUID.fromString((String) payload.get("matchId"));
        matchService.resign(userId, matchId);
    }

    @MessageMapping("/match.sync")
    public void sync(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        UUID matchId = UUID.fromString((String) payload.get("matchId"));
        matchService.syncMatch(userId, matchId);
    }

    @MessageMapping("/challenge.send")
    public void sendChallenge(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        String targetUsername = (String) payload.get("targetUsername");
        int digitMode = ((Number) payload.get("digitMode")).intValue();
        String timeControl = (String) payload.get("timeControl");
        try {
            challengeService.sendChallenge(userId, targetUsername, digitMode, timeControl);
        } catch (Exception e) {
            // Swallow silently — challenger will not see error WS-side
        }
    }

    @MessageMapping("/challenge.accept")
    public void acceptChallenge(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        UUID challengeId = UUID.fromString((String) payload.get("challengeId"));
        challengeService.acceptChallenge(userId, challengeId);
    }

    @MessageMapping("/challenge.decline")
    public void declineChallenge(Principal principal, @Payload Map<String, Object> payload) {
        UUID userId = getUserId(principal);
        if (userId == null) return;

        UUID challengeId = UUID.fromString((String) payload.get("challengeId"));
        challengeService.declineChallenge(userId, challengeId);
    }

    private UUID getUserId(Principal principal) {
        if (principal instanceof UserPrincipal up) {
            return up.getUser().getId();
        }
        return null;
    }
}
