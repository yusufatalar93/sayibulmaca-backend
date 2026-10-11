package com.sayibulmaca.service;

import com.sayibulmaca.config.GameConfig;
import com.sayibulmaca.game.*;
import com.sayibulmaca.model.Rating;
import com.sayibulmaca.model.RatingId;
import com.sayibulmaca.model.User;
import com.sayibulmaca.repository.RatingRepository;
import com.sayibulmaca.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPersistenceService persistenceService;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messaging;
    private final GameConfig gameConfig;

    private final Map<UUID, MatchState> activeMatches = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToMatch = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> setupStartedAt = new ConcurrentHashMap<>();

    // ── Match creation ──────────────────────────────────────────────────────────

    public void createMatch(UUID playerAId, UUID playerBId, int digitMode, TimeControl timeControl) {
        UUID matchId = UUID.randomUUID();
        long timePerPlayerMs = getTimePerPlayerMs(digitMode, timeControl);

        persistenceService.createMatch(matchId, playerAId, playerBId, digitMode, timeControl);

        MatchState state = MatchState.create(matchId, playerAId, playerBId, digitMode, timeControl, timePerPlayerMs);
        activeMatches.put(matchId, state);
        playerToMatch.put(playerAId, matchId);
        playerToMatch.put(playerBId, matchId);
        setupStartedAt.put(matchId, Instant.now());

        User playerA = userRepository.findById(playerAId).orElseThrow();
        User playerB = userRepository.findById(playerBId).orElseThrow();
        Rating ratingA = getRating(playerAId, (short) digitMode);
        Rating ratingB = getRating(playerBId, (short) digitMode);

        // Send MATCH_FOUND (different per player — each sees opponent info)
        sendToPlayer(playerAId, Map.of(
                "type", "MATCH_FOUND",
                "data", Map.of(
                        "matchId", matchId.toString(),
                        "iAmPlayerA", true,
                        "opponentUsername", safeUsername(playerB),
                        "opponentLeague", LeagueUtil.getLeague(ratingB.getRating()),
                        "opponentTier", nullSafe(LeagueUtil.getTier(ratingB.getRating())),
                        "opponentRating", ratingB.getRating(),
                        "digitMode", digitMode,
                        "timeControl", timeControl.name()
                ),
                "serverTime", System.currentTimeMillis()
        ));

        sendToPlayer(playerBId, Map.of(
                "type", "MATCH_FOUND",
                "data", Map.of(
                        "matchId", matchId.toString(),
                        "iAmPlayerA", false,
                        "opponentUsername", safeUsername(playerA),
                        "opponentLeague", LeagueUtil.getLeague(ratingA.getRating()),
                        "opponentTier", nullSafe(LeagueUtil.getTier(ratingA.getRating())),
                        "opponentRating", ratingA.getRating(),
                        "digitMode", digitMode,
                        "timeControl", timeControl.name()
                ),
                "serverTime", System.currentTimeMillis()
        ));

        // Send SECRET_PHASE to both
        Object secretPhaseMsg = Map.of(
                "type", "SECRET_PHASE",
                "data", Map.of(
                        "matchId", matchId.toString(),
                        "timeoutSeconds", gameConfig.getSecretSetupTimeoutSeconds()
                ),
                "serverTime", System.currentTimeMillis()
        );
        sendToPlayer(playerAId, secretPhaseMsg);
        sendToPlayer(playerBId, secretPhaseMsg);
    }

    // ── Secret phase ────────────────────────────────────────────────────────────

    public void setSecret(UUID playerId, UUID matchId, String secret) {
        MatchState state = activeMatches.get(matchId);
        if (state == null || state.getStatus() != MatchStatus.SETUP) return;
        if (!state.isPlayerA(playerId) && !state.isPlayerB(playerId)) return;

        state.setSecret(playerId, secret);

        if (state.areBothReady()) {
            startGame(matchId, state);
        }
    }

    public void autoAssignSecret(UUID playerId, UUID matchId) {
        MatchState state = activeMatches.get(matchId);
        if (state == null || state.getStatus() != MatchStatus.SETUP) return;
        if (!state.isPlayerA(playerId) && !state.isPlayerB(playerId)) return;

        state.autoAssignSecret(playerId);

        String assignedSecret = state.isPlayerA(playerId) ? state.getSecretA() : state.getSecretB();
        sendToPlayer(playerId, Map.of(
                "type", "SECRET_ASSIGNED",
                "data", Map.of("secret", assignedSecret),
                "serverTime", System.currentTimeMillis()
        ));

        if (state.areBothReady()) {
            startGame(matchId, state);
        }
    }

    private void startGame(UUID matchId, MatchState state) {
        setupStartedAt.remove(matchId);
        state.startMatch();

        persistenceService.updateMatchStarted(matchId, state.getSecretA(), state.getSecretB());

        Object matchStartedMsg = Map.of(
                "type", "MATCH_STARTED",
                "data", Map.of(
                        "matchId", matchId.toString(),
                        "playerAId", state.getPlayerAId().toString(),
                        "playerBId", state.getPlayerBId().toString(),
                        "currentTurnPlayerId", state.getCurrentTurnPlayerId().toString(),
                        "clockAMs", state.getClockA().getRemainingMs(),
                        "clockBMs", state.getClockB().getRemainingMs()
                ),
                "serverTime", System.currentTimeMillis()
        );
        sendToPlayer(state.getPlayerAId(), matchStartedMsg);
        sendToPlayer(state.getPlayerBId(), matchStartedMsg);
    }

    // ── Gameplay ─────────────────────────────────────────────────────────────────

    public void makeGuess(UUID playerId, UUID matchId, String value) {
        MatchState state = activeMatches.get(matchId);
        if (state == null) return;

        if (state.getStatus() != MatchStatus.IN_PROGRESS) {
            sendRejected(playerId, matchId, "Match not in progress");
            return;
        }
        if (!playerId.equals(state.getCurrentTurnPlayerId())) {
            sendRejected(playerId, matchId, "Not your turn");
            return;
        }
        if (!NumberValidator.isValid(value, state.getDigitMode())) {
            sendRejected(playerId, matchId, "Invalid guess");
            return;
        }

        boolean wasInFinalTurn = state.isFinalTurnActive();

        GuessResult result;
        try {
            result = state.makeGuess(playerId, value);
        } catch (IllegalArgumentException e) {
            sendRejected(playerId, matchId, e.getMessage());
            return;
        } catch (IllegalStateException e) {
            sendRejected(playerId, matchId, e.getMessage());
            return;
        }

        boolean isPlayerA = state.isPlayerA(playerId);
        int guessNumber = isPlayerA ? state.getGuessCountA() : state.getGuessCountB();
        long timeLeftMs = isPlayerA ? state.getClockA().getRemainingMs() : state.getClockB().getRemainingMs();

        persistenceService.saveGuess(matchId, playerId, guessNumber, value, result.plus(), result.minus(), (int) timeLeftMs);

        // Send GUESS_RESULT to both players
        Object guessResultMsg = Map.of(
                "type", "GUESS_RESULT",
                "data", buildMap(
                        "matchId", matchId.toString(),
                        "guesserId", playerId.toString(),
                        "guessNumber", guessNumber,
                        "value", value,
                        "plus", result.plus(),
                        "minus", result.minus(),
                        "clockAMs", state.getClockA().getRemainingMs(),
                        "clockBMs", state.getClockB().getRemainingMs(),
                        "currentTurnPlayerId", state.getStatus() == MatchStatus.IN_PROGRESS
                                ? state.getCurrentTurnPlayerId().toString() : null
                ),
                "serverTime", System.currentTimeMillis()
        );
        sendToPlayer(state.getPlayerAId(), guessResultMsg);
        sendToPlayer(state.getPlayerBId(), guessResultMsg);

        // Check if final turn was just activated
        if (!wasInFinalTurn && state.isFinalTurnActive()) {
            Object finalTurnMsg = Map.of(
                    "type", "FINAL_TURN",
                    "data", Map.of("matchId", matchId.toString()),
                    "serverTime", System.currentTimeMillis()
            );
            // Send to the player who now has the final turn
            sendToPlayer(state.getCurrentTurnPlayerId(), finalTurnMsg);
        }

        if (state.getStatus() == MatchStatus.FINISHED || state.getStatus() == MatchStatus.ABORTED) {
            finalizeMatch(matchId, state);
        }
    }

    public void resign(UUID playerId, UUID matchId) {
        MatchState state = activeMatches.get(matchId);
        if (state == null || state.getStatus() != MatchStatus.IN_PROGRESS) return;

        state.resign(playerId);
        finalizeMatch(matchId, state);
    }

    public void syncMatch(UUID playerId, UUID matchId) {
        MatchState state = activeMatches.get(matchId);
        if (state == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("matchId", matchId.toString());
        data.put("status", state.getStatus().name());
        data.put("playerAId", state.getPlayerAId().toString());
        data.put("playerBId", state.getPlayerBId().toString());
        data.put("clockAMs", state.getClockA().getRemainingMs());
        data.put("clockBMs", state.getClockB().getRemainingMs());
        data.put("iAmPlayerA", state.isPlayerA(playerId));

        if (state.getStatus() == MatchStatus.IN_PROGRESS && state.getCurrentTurnPlayerId() != null) {
            data.put("currentTurnPlayerId", state.getCurrentTurnPlayerId().toString());
        }

        List<Map<String, Object>> myGuesses = new ArrayList<>();
        List<Map<String, Object>> opponentGuesses = new ArrayList<>();
        boolean isPlayerA = state.isPlayerA(playerId);

        for (MatchState.GuessEntry ge : (isPlayerA ? state.getGuessesA() : state.getGuessesB())) {
            myGuesses.add(Map.of("value", ge.guess(), "plus", ge.plus(), "minus", ge.minus()));
        }
        for (MatchState.GuessEntry ge : (isPlayerA ? state.getGuessesB() : state.getGuessesA())) {
            opponentGuesses.add(Map.of("value", ge.guess(), "plus", ge.plus(), "minus", ge.minus()));
        }
        data.put("myGuesses", myGuesses);
        data.put("opponentGuesses", opponentGuesses);
        data.put("finalTurnActive", state.isFinalTurnActive());

        String mySecret = isPlayerA ? state.getSecretA() : state.getSecretB();
        if (mySecret != null) {
            data.put("mySecret", mySecret);
        }

        sendToPlayer(playerId, Map.of(
                "type", "MATCH_STATE",
                "data", data,
                "serverTime", System.currentTimeMillis()
        ));
    }

    // ── Match finalization ───────────────────────────────────────────────────────

    private void finalizeMatch(UUID matchId, MatchState state) {
        activeMatches.remove(matchId);
        playerToMatch.remove(state.getPlayerAId());
        playerToMatch.remove(state.getPlayerBId());
        setupStartedAt.remove(matchId);

        MatchPersistenceService.FinalizeResult fin = persistenceService.finalizeMatch(matchId, state);

        boolean isAbort = state.getEndReason() == EndReason.ABORT;

        // Determine result strings for each player
        String resultForA = resultString(state.getResult(), true);
        String resultForB = resultString(state.getResult(), false);

        Map<String, Object> baseData = buildMap(
                "matchId", matchId.toString(),
                "endReason", state.getEndReason() != null ? state.getEndReason().name() : null
        );

        Map<String, Object> dataForA = new HashMap<>(baseData);
        dataForA.put("result", resultForA);
        dataForA.put("opponentSecret", state.getSecretB());
        dataForA.put("myRatingChange", isAbort ? 0 : fin.ratingChangeA());
        dataForA.put("newRating", fin.newRatingA());
        dataForA.put("newLeague", fin.newLeagueA());
        dataForA.put("newTier", fin.newTierA());

        Map<String, Object> dataForB = new HashMap<>(baseData);
        dataForB.put("result", resultForB);
        dataForB.put("opponentSecret", state.getSecretA());
        dataForB.put("myRatingChange", isAbort ? 0 : fin.ratingChangeB());
        dataForB.put("newRating", fin.newRatingB());
        dataForB.put("newLeague", fin.newLeagueB());
        dataForB.put("newTier", fin.newTierB());

        sendToPlayer(state.getPlayerAId(), Map.of("type", "MATCH_ENDED", "data", dataForA, "serverTime", System.currentTimeMillis()));
        sendToPlayer(state.getPlayerBId(), Map.of("type", "MATCH_ENDED", "data", dataForB, "serverTime", System.currentTimeMillis()));
    }

    // ── Scheduler ────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 1000)
    public void checkActiveMatches() {
        // Secret setup timeout
        for (Map.Entry<UUID, Instant> entry : new HashMap<>(setupStartedAt).entrySet()) {
            UUID matchId = entry.getKey();
            if (Instant.now().isAfter(entry.getValue().plusSeconds(gameConfig.getSecretSetupTimeoutSeconds()))) {
                handleSecretTimeout(matchId);
            }
        }

        // Clock and first-move timeouts
        for (Map.Entry<UUID, MatchState> entry : new HashMap<>(activeMatches).entrySet()) {
            UUID matchId = entry.getKey();
            MatchState state = entry.getValue();

            if (state.getStatus() != MatchStatus.IN_PROGRESS) continue;

            // First move abort: if no guesses and time since start > firstMoveTimeoutSeconds
            if (state.getGuessCountA() + state.getGuessCountB() == 0 && state.getStartedAt() != null) {
                if (Instant.now().isAfter(state.getStartedAt().plusSeconds(gameConfig.getFirstMoveTimeoutSeconds()))) {
                    state.abort();
                    finalizeMatch(matchId, state);
                    continue;
                }
            }

            // Clock timeout
            state.checkTimeout();
            if (state.getStatus() == MatchStatus.FINISHED) {
                finalizeMatch(matchId, state);
            }
        }
    }

    private void handleSecretTimeout(UUID matchId) {
        MatchState state = activeMatches.get(matchId);
        if (state == null || state.getStatus() != MatchStatus.SETUP) {
            setupStartedAt.remove(matchId);
            return;
        }
        setupStartedAt.remove(matchId);

        if (!state.isPlayerAReady()) {
            state.autoAssignSecret(state.getPlayerAId());
        }
        if (!state.isPlayerBReady()) {
            state.autoAssignSecret(state.getPlayerBId());
        }
        startGame(matchId, state);
    }

    // ── Queue status ─────────────────────────────────────────────────────────────

    public void sendQueueStatus(UUID userId, long waitSeconds, int currentRange) {
        sendToPlayer(userId, Map.of(
                "type", "QUEUE_STATUS",
                "data", Map.of("waitingSeconds", waitSeconds, "currentRange", currentRange),
                "serverTime", System.currentTimeMillis()
        ));
    }

    // ── Utility ──────────────────────────────────────────────────────────────────

    public Optional<UUID> getActiveMatchId(UUID userId) {
        return Optional.ofNullable(playerToMatch.get(userId));
    }

    public Optional<String> getActiveMatchPhase(UUID userId) {
        UUID matchId = playerToMatch.get(userId);
        if (matchId == null) return Optional.empty();
        MatchState state = activeMatches.get(matchId);
        if (state == null) return Optional.empty();
        return Optional.of(state.getStatus() == MatchStatus.SETUP ? "secret" : "playing");
    }

    private void sendToPlayer(UUID userId, Object message) {
        messaging.convertAndSendToUser(userId.toString(), "/queue/game", message);
    }

    private void sendRejected(UUID playerId, UUID matchId, String reason) {
        sendToPlayer(playerId, Map.of(
                "type", "GUESS_REJECTED",
                "data", Map.of("matchId", matchId.toString(), "reason", reason),
                "serverTime", System.currentTimeMillis()
        ));
    }

    private long getTimePerPlayerMs(int digitMode, TimeControl timeControl) {
        String modeKey = digitMode == 4 ? "digits-4" : "digits-5";
        String tcKey = timeControl.name().toLowerCase();
        Map<String, Integer> tcMap = gameConfig.getTimeControl().get(tcKey);
        if (tcMap == null) return 240_000L;
        Integer seconds = tcMap.get(modeKey);
        return seconds != null ? seconds * 1000L : 240_000L;
    }

    private Rating getRating(UUID userId, short digitMode) {
        return ratingRepository.findById(new RatingId(userId, digitMode))
                .orElseGet(() -> {
                    Rating r = new Rating();
                    r.setRating(1000);
                    r.setGamesPlayed(0);
                    return r;
                });
    }

    private String safeUsername(User user) {
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }

    private Object nullSafe(String val) {
        return val != null ? val : "";
    }

    private String resultString(MatchResult result, boolean forPlayerA) {
        if (result == null) return "draw";
        return switch (result) {
            case DRAW -> "draw";
            case A_WON -> forPlayerA ? "won" : "lost";
            case B_WON -> forPlayerA ? "lost" : "won";
        };
    }

    /** Helper to build a Map without null values being rejected by Map.of() */
    private Map<String, Object> buildMap(Object... keysAndValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length - 1; i += 2) {
            if (keysAndValues[i + 1] != null) {
                map.put((String) keysAndValues[i], keysAndValues[i + 1]);
            } else {
                map.put((String) keysAndValues[i], "");
            }
        }
        return map;
    }
}
