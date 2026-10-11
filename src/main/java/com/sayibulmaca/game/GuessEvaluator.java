package com.sayibulmaca.game;

/**
 * Utility class for evaluating a guess against a secret number.
 */
public final class GuessEvaluator {

    private GuessEvaluator() {
        // Prevent instantiation
    }

    /**
     * Evaluates a guess against a secret number.
     *
     * @param secret the actual secret number
     * @param guess the guessed number
     * @return a GuessResult containing the plus and minus counts, and the digit mode
     */
    public static GuessResult evaluate(String secret, String guess) {
        if (secret == null || guess == null || secret.length() != guess.length()) {
            throw new IllegalArgumentException("Secret and guess must be non-null and of the same length.");
        }

        int plus = 0;
        int minus = 0;
        int length = secret.length();

        for (int i = 0; i < length; i++) {
            char guessChar = guess.charAt(i);
            if (guessChar == secret.charAt(i)) {
                plus++;
            } else if (secret.indexOf(guessChar) != -1) {
                minus++;
            }
        }

        return new GuessResult(plus, minus, length);
    }
}
