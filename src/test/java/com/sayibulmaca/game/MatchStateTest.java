package com.sayibulmaca.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MatchStateTest {

    private UUID matchId;
    private UUID playerAId;
    private UUID playerBId;
    private MatchState match;

    @BeforeEach
    void setUp() {
        matchId = UUID.randomUUID();
        playerAId = UUID.randomUUID();
        playerBId = UUID.randomUUID();
        match = MatchState.create(matchId, playerAId, playerBId, 4, TimeControl.NORMAL, 600000);
    }

    @Test
    void testSetupSecrets() {
        assertEquals(MatchStatus.SETUP, match.getStatus());

        match.setSecret(playerAId, "1234");
        assertFalse(match.areBothReady());

        assertThrows(IllegalArgumentException.class, () -> match.setSecret(playerBId, "1123"), "Should reject invalid secret");

        match.autoAssignSecret(playerBId);
        assertTrue(match.areBothReady());
    }

    @Test
    void testTurnOrderAndGuessing() {
        match.setSecret(playerAId, "1234");
        match.setSecret(playerBId, "5678");
        match.startMatch();

        assertEquals(MatchStatus.IN_PROGRESS, match.getStatus());

        UUID firstPlayer = match.getCurrentTurnPlayerId();
        UUID secondPlayer = match.getOpponentId(firstPlayer);

        assertThrows(IllegalStateException.class, () -> match.makeGuess(secondPlayer, "1234"), "Wrong player guessing");
        
        match.makeGuess(firstPlayer, "4321");
        
        assertThrows(IllegalArgumentException.class, () -> match.makeGuess(secondPlayer, "4444"), "Invalid guess");
        
        match.makeGuess(secondPlayer, "8765");
        
        // Duplicate guess
        assertThrows(IllegalArgumentException.class, () -> match.makeGuess(firstPlayer, "4321"));
    }

    @Test
    void testASolvesBGetsFinalTurnAndSolves() {
        match.setSecret(playerAId, "1234");
        match.setSecret(playerBId, "5678");
        match.startMatch();

        UUID pA = match.getCurrentTurnPlayerId();
        UUID pB = match.getOpponentId(pA);

        // pA guesses correctly -> +4
        String pBSecret = match.isPlayerA(pB) ? "1234" : "5678";
        
        match.makeGuess(pA, pBSecret);
        
        assertTrue(match.isFinalTurnActive());
        assertEquals(MatchStatus.IN_PROGRESS, match.getStatus());
        assertEquals(pB, match.getCurrentTurnPlayerId());

        // pB guesses correctly -> DRAW
        String pASecret = match.isPlayerA(pA) ? "1234" : "5678";
        match.makeGuess(pB, pASecret);

        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(MatchResult.DRAW, match.getResult());
        assertEquals(EndReason.SOLVED, match.getEndReason());
    }

    @Test
    void testASolvesBGetsFinalTurnAndFails() {
        match.setSecret(playerAId, "1234");
        match.setSecret(playerBId, "5678");
        match.startMatch();

        UUID pA = match.getCurrentTurnPlayerId();
        UUID pB = match.getOpponentId(pA);

        String pBSecret = match.isPlayerA(pB) ? "1234" : "5678";
        match.makeGuess(pA, pBSecret);
        
        assertTrue(match.isFinalTurnActive());

        // pB guesses wrong
        match.makeGuess(pB, "9876");

        assertEquals(MatchStatus.FINISHED, match.getStatus());
        MatchResult expectedResult = match.isPlayerA(pA) ? MatchResult.A_WON : MatchResult.B_WON;
        assertEquals(expectedResult, match.getResult());
    }

    @Test
    void testBSolvesNormally() {
        match.setSecret(playerAId, "1234");
        match.setSecret(playerBId, "5678");
        match.startMatch();

        UUID pA = match.getCurrentTurnPlayerId();
        UUID pB = match.getOpponentId(pA);

        // A guesses wrong
        match.makeGuess(pA, "9876");

        // B guesses correctly
        String pASecret = match.isPlayerA(pA) ? "1234" : "5678";
        match.makeGuess(pB, pASecret);

        assertFalse(match.isFinalTurnActive());
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        MatchResult expectedResult = match.isPlayerA(pB) ? MatchResult.A_WON : MatchResult.B_WON;
        assertEquals(expectedResult, match.getResult());
    }

    @Test
    void testTimeout() throws InterruptedException {
        // Fast time per player
        match = MatchState.create(matchId, playerAId, playerBId, 4, TimeControl.FAST, 50);
        match.setSecret(playerAId, "1234");
        match.setSecret(playerBId, "5678");
        match.startMatch();

        Thread.sleep(100); // let it timeout

        match.checkTimeout();
        
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(EndReason.TIMEOUT, match.getEndReason());
    }

    @Test
    void testResign() {
        match.setSecret(playerAId, "1234");
        match.setSecret(playerBId, "5678");
        match.startMatch();

        match.resign(playerAId);
        
        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(MatchResult.B_WON, match.getResult());
        assertEquals(EndReason.RESIGN, match.getEndReason());
    }

    @Test
    void testGuessLimit() {
        match.setSecret(playerAId, "1234");
        match.setSecret(playerBId, "5678");
        match.startMatch();

        UUID pA = match.getCurrentTurnPlayerId();
        UUID pB = match.getOpponentId(pA);

        List<String> validGuessesA = new ArrayList<>();
        List<String> validGuessesB = new ArrayList<>();

        for (int i = 1000; i < 9999; i++) {
            String s = String.valueOf(i);
            if (NumberValidator.isValid(s, 4)) {
                if (!s.equals("1234") && !s.equals("5678")) {
                    if (validGuessesA.size() < 30) {
                        validGuessesA.add(s);
                    } else if (validGuessesB.size() < 30) {
                        validGuessesB.add(s);
                    } else {
                        break;
                    }
                }
            }
        }

        // Generate 30 unique wrong guesses
        for (int i = 0; i < 30; i++) {
            match.makeGuess(pA, validGuessesA.get(i));
            match.makeGuess(pB, validGuessesB.get(i));
        }

        assertEquals(MatchStatus.FINISHED, match.getStatus());
        assertEquals(MatchResult.DRAW, match.getResult());
        assertEquals(EndReason.GUESS_LIMIT, match.getEndReason());
    }
}
