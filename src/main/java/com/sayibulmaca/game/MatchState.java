package com.sayibulmaca.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;

/**
 * Represents the complete in-memory state of a match.
 * Responsible for handling turn logic, guesses, end conditions, and state transitions.
 * All state mutations are synchronized.
 */
public class MatchState {

    public record GuessEntry(String guess, int plus, int minus, long remainingMs) {}

    private final UUID matchId;
    private final UUID playerAId;
    private final UUID playerBId;
    private final int digitMode;
    private final TimeControl timeControl;

    private MatchStatus status;
    private MatchResult result;
    private EndReason endReason;

    private String secretA;
    private String secretB;

    private final PlayerClock clockA;
    private final PlayerClock clockB;

    private UUID currentTurnPlayerId;
    private final List<GuessEntry> guessesA;
    private final List<GuessEntry> guessesB;

    private int guessCountA;
    private int guessCountB;

    private boolean playerAReady;
    private boolean playerBReady;

    private Instant startedAt;

    private boolean finalTurnActive;
    private int finalTurnNumber;

    private static final int MAX_GUESSES = 30;

    private MatchState(UUID matchId, UUID playerAId, UUID playerBId, int digitMode, TimeControl timeControl, long timePerPlayerMs) {
        this.matchId = matchId;
        this.playerAId = playerAId;
        this.playerBId = playerBId;
        this.digitMode = digitMode;
        this.timeControl = timeControl;
        this.status = MatchStatus.SETUP;
        this.clockA = new PlayerClock(timePerPlayerMs);
        this.clockB = new PlayerClock(timePerPlayerMs);
        this.guessesA = new ArrayList<>();
        this.guessesB = new ArrayList<>();
    }

    /**
     * Creates a new match in SETUP status.
     */
    public static MatchState create(UUID matchId, UUID playerAId, UUID playerBId, int digitMode, TimeControl timeControl, long timePerPlayerMs) {
        return new MatchState(matchId, playerAId, playerBId, digitMode, timeControl, timePerPlayerMs);
    }

    public synchronized void setSecret(UUID playerId, String secret) {
        if (status != MatchStatus.SETUP) {
            throw new IllegalStateException("Cannot set secret in current status: " + status);
        }
        if (!NumberValidator.isValid(secret, digitMode)) {
            throw new IllegalArgumentException("Invalid secret number");
        }

        if (isPlayerA(playerId)) {
            secretA = secret;
            playerAReady = true;
        } else if (isPlayerB(playerId)) {
            secretB = secret;
            playerBReady = true;
        } else {
            throw new IllegalArgumentException("Player not in this match");
        }
    }

    public synchronized void autoAssignSecret(UUID playerId) {
        String randomSecret = NumberValidator.generateRandomSecret(digitMode);
        setSecret(playerId, randomSecret);
    }

    public synchronized boolean areBothReady() {
        return playerAReady && playerBReady;
    }

    public synchronized void startMatch() {
        if (status != MatchStatus.SETUP) {
            throw new IllegalStateException("Match already started or aborted");
        }
        if (!areBothReady()) {
            throw new IllegalStateException("Both players must be ready");
        }
        status = MatchStatus.IN_PROGRESS;
        startedAt = Instant.now();

        // Randomly pick who goes first
        Random random = new Random();
        if (random.nextBoolean()) {
            currentTurnPlayerId = playerAId;
            clockA.start();
        } else {
            currentTurnPlayerId = playerBId;
            clockB.start();
        }
    }

    public synchronized GuessResult makeGuess(UUID playerId, String guess) {
        if (status != MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Match is not in progress");
        }
        if (!playerId.equals(currentTurnPlayerId)) {
            throw new IllegalStateException("Not your turn");
        }
        if (!NumberValidator.isValid(guess, digitMode)) {
            throw new IllegalArgumentException("Invalid guess number");
        }

        checkTimeout();
        if (status != MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Match ended by timeout");
        }

        boolean isA = isPlayerA(playerId);
        List<GuessEntry> currentGuesses = isA ? guessesA : guessesB;
        for (GuessEntry ge : currentGuesses) {
            if (ge.guess().equals(guess)) {
                throw new IllegalArgumentException("Duplicate guess");
            }
        }

        String opponentSecret = isA ? secretB : secretA;
        GuessResult resultObj = GuessEvaluator.evaluate(opponentSecret, guess);
        
        long remaining = isA ? clockA.getRemainingMs() : clockB.getRemainingMs();
        GuessEntry entry = new GuessEntry(guess, resultObj.plus(), resultObj.minus(), remaining);
        
        if (isA) {
            guessesA.add(entry);
            guessCountA++;
            clockA.stop();
            if (!resultObj.isSolved() && !finalTurnActive) {
                clockB.start();
            }
        } else {
            guessesB.add(entry);
            guessCountB++;
            clockB.stop();
            if (!resultObj.isSolved() && !finalTurnActive) {
                clockA.start();
            }
        }

        handleTurnEnd(playerId, resultObj.isSolved());

        return resultObj;
    }

    private void handleTurnEnd(UUID playerId, boolean solved) {
        boolean isA = isPlayerA(playerId);

        if (solved) {
            if (finalTurnActive) {
                // It was final turn (B playing). B solved. DRAW.
                endMatch(MatchResult.DRAW, EndReason.SOLVED);
            } else {
                // Someone solved normally. Check if equal-turn rule applies.
                // The equal turn rule: If player A (first mover) solves, B gets final turn.
                // However, our code assigns first mover randomly. Let's define "first mover" as whoever had guessCount = 1 when the other had 0.
                // Wait, if A just moved, A's guess count is N. B's is N-1. This means A went first.
                // If B's guess count is N, and A's is N, it means B went second.
                int aCount = guessCountA;
                int bCount = guessCountB;
                
                if (isA) {
                    if (aCount > bCount) {
                        // A went first. Activate final turn for B.
                        finalTurnActive = true;
                        finalTurnNumber = aCount;
                        currentTurnPlayerId = playerBId;
                        clockB.start();
                    } else {
                        // A went second. No final turn. A wins.
                        endMatch(MatchResult.A_WON, EndReason.SOLVED);
                    }
                } else {
                    if (bCount > aCount) {
                        // B went first. Activate final turn for A.
                        finalTurnActive = true;
                        finalTurnNumber = bCount;
                        currentTurnPlayerId = playerAId;
                        clockA.start();
                    } else {
                        // B went second. No final turn. B wins.
                        endMatch(MatchResult.B_WON, EndReason.SOLVED);
                    }
                }
            }
            return;
        }

        if (finalTurnActive) {
            // Player missed on final turn. The other player wins.
            if (isA) {
                // A missed final turn. B wins.
                endMatch(MatchResult.B_WON, EndReason.SOLVED);
            } else {
                // B missed final turn. A wins.
                endMatch(MatchResult.A_WON, EndReason.SOLVED);
            }
            return;
        }

        // Switch turn
        currentTurnPlayerId = isA ? playerBId : playerAId;

        // Check guess limit
        if (guessCountA >= MAX_GUESSES && guessCountB >= MAX_GUESSES) {
            endMatch(MatchResult.DRAW, EndReason.GUESS_LIMIT);
        }
    }

    public synchronized void checkTimeout() {
        if (status != MatchStatus.IN_PROGRESS) return;
        
        if (clockA.isExpired()) {
            endMatch(MatchResult.B_WON, EndReason.TIMEOUT);
        } else if (clockB.isExpired()) {
            endMatch(MatchResult.A_WON, EndReason.TIMEOUT);
        }
    }

    public synchronized void resign(UUID playerId) {
        if (status != MatchStatus.IN_PROGRESS) return;
        if (isPlayerA(playerId)) {
            endMatch(MatchResult.B_WON, EndReason.RESIGN);
        } else if (isPlayerB(playerId)) {
            endMatch(MatchResult.A_WON, EndReason.RESIGN);
        }
    }

    public synchronized void abort() {
        if (status == MatchStatus.FINISHED || status == MatchStatus.ABORTED) return;
        status = MatchStatus.ABORTED;
        endReason = EndReason.ABORT;
        clockA.stop();
        clockB.stop();
    }

    private void endMatch(MatchResult res, EndReason reason) {
        this.status = MatchStatus.FINISHED;
        this.result = res;
        this.endReason = reason;
        clockA.stop();
        clockB.stop();
        currentTurnPlayerId = null;
    }

    public boolean isPlayerA(UUID playerId) {
        return playerAId.equals(playerId);
    }

    public boolean isPlayerB(UUID playerId) {
        return playerBId.equals(playerId);
    }

    public UUID getOpponentId(UUID playerId) {
        if (isPlayerA(playerId)) return playerBId;
        if (isPlayerB(playerId)) return playerAId;
        throw new IllegalArgumentException("Player not in this match");
    }

    // Secret getters (for persistence only — never send to clients directly)
    public String getSecretA() { return secretA; }
    public String getSecretB() { return secretB; }

    // Ready flags (for auto-assign logic)
    public boolean isPlayerAReady() { return playerAReady; }
    public boolean isPlayerBReady() { return playerBReady; }

    // Getters
    public UUID getMatchId() { return matchId; }
    public UUID getPlayerAId() { return playerAId; }
    public UUID getPlayerBId() { return playerBId; }
    public int getDigitMode() { return digitMode; }
    public TimeControl getTimeControl() { return timeControl; }
    public MatchStatus getStatus() { return status; }
    public MatchResult getResult() { return result; }
    public EndReason getEndReason() { return endReason; }
    public PlayerClock getClockA() { return clockA; }
    public PlayerClock getClockB() { return clockB; }
    public UUID getCurrentTurnPlayerId() { return currentTurnPlayerId; }
    public List<GuessEntry> getGuessesA() { return new ArrayList<>(guessesA); }
    public List<GuessEntry> getGuessesB() { return new ArrayList<>(guessesB); }
    public int getGuessCountA() { return guessCountA; }
    public int getGuessCountB() { return guessCountB; }
    public Instant getStartedAt() { return startedAt; }
    public boolean isFinalTurnActive() { return finalTurnActive; }
    public int getFinalTurnNumber() { return finalTurnNumber; }
}
