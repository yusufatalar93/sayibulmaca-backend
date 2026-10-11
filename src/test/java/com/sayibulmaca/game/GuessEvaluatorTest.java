package com.sayibulmaca.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuessEvaluatorTest {

    @Test
    void testEvaluationScenarios() {
        // secret=1234, guess=4368 → +0 -2
        GuessResult res1 = GuessEvaluator.evaluate("1234", "4368");
        assertEquals(0, res1.plus());
        assertEquals(2, res1.minus());
        assertFalse(res1.isSolved());

        // secret=1234, guess=1243 → +2 -2
        GuessResult res2 = GuessEvaluator.evaluate("1234", "1243");
        assertEquals(2, res2.plus());
        assertEquals(2, res2.minus());
        assertFalse(res2.isSolved());

        // secret=1234, guess=1234 → +4 -0 (solved)
        GuessResult res3 = GuessEvaluator.evaluate("1234", "1234");
        assertEquals(4, res3.plus());
        assertEquals(0, res3.minus());
        assertTrue(res3.isSolved());

        // secret=1234, guess=5678 → +0 -0
        GuessResult res4 = GuessEvaluator.evaluate("1234", "5678");
        assertEquals(0, res4.plus());
        assertEquals(0, res4.minus());
        assertFalse(res4.isSolved());

        // secret=12345, guess=12345 → +5 -0 (5-digit solved)
        GuessResult res5 = GuessEvaluator.evaluate("12345", "12345");
        assertEquals(5, res5.plus());
        assertEquals(0, res5.minus());
        assertTrue(res5.isSolved());

        // secret=12345, guess=54321 → +1 -4 (3 in middle is match for 12345 vs 54321)
        // Wait, 12345 vs 54321 -> 1 at pos 0 (guess is 5), 2 at pos 1 (guess is 4), 3 at pos 2 (guess is 3). Yes, 3 matches exactly.
        // Let's test it:
        GuessResult res6 = GuessEvaluator.evaluate("12345", "54321");
        assertEquals(1, res6.plus()); // '3' is +1
        assertEquals(4, res6.minus()); // '5','4','2','1' are -4
        assertFalse(res6.isSolved());
    }
}
