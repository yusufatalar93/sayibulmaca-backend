package com.sayibulmaca.game;

/**
 * Holds the result of a guess evaluation.
 *
 * @param plus the number of correct digits in the correct positions
 * @param minus the number of correct digits in the wrong positions
 * @param digitMode the number of digits in the game, used to determine if the game is solved
 */
public record GuessResult(int plus, int minus, int digitMode) {
    /**
     * Checks if the guess was a complete match.
     * @return true if the number of pluses equals the digit mode
     */
    public boolean isSolved() {
        return plus == digitMode;
    }
}
