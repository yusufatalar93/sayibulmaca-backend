package com.sayibulmaca.game;

/**
 * Represents the mode of the game in terms of the number of digits.
 */
public enum DigitMode {
    FOUR(4),
    FIVE(5);

    private final int digits;

    DigitMode(int digits) {
        this.digits = digits;
    }

    /**
     * Gets the number of digits for this mode.
     * @return the number of digits
     */
    public int getDigits() {
        return digits;
    }

    /**
     * Converts an integer to a DigitMode.
     * @param mode the integer mode (4 or 5)
     * @return the corresponding DigitMode
     * @throws IllegalArgumentException if mode is not 4 or 5
     */
    public static DigitMode fromInt(int mode) {
        if (mode == 4) return FOUR;
        if (mode == 5) return FIVE;
        throw new IllegalArgumentException("Unsupported digit mode: " + mode);
    }
}
