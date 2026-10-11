package com.sayibulmaca.game;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Utility class for validating and generating valid game numbers.
 */
public final class NumberValidator {
    
    private NumberValidator() {
        // Prevent instantiation
    }

    /**
     * Validates a given number string against the rules:
     * - Exactly N digits
     * - Digits only
     * - All digits unique
     * - First digit not 0
     *
     * @param number the string to validate
     * @param digitMode the required number of digits (4 or 5)
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String number, int digitMode) {
        if (number == null || number.length() != digitMode) {
            return false;
        }

        if (number.charAt(0) == '0') {
            return false;
        }

        int seen = 0;
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int digit = c - '0';
            if ((seen & (1 << digit)) != 0) {
                return false; // Duplicate digit
            }
            seen |= (1 << digit);
        }

        return true;
    }

    /**
     * Generates a valid random secret number.
     *
     * @param digitMode the required number of digits (4 or 5)
     * @return a valid randomly generated string
     */
    public static String generateRandomSecret(int digitMode) {
        List<Integer> digits = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            digits.add(i);
        }

        Collections.shuffle(digits);

        // Ensure the first digit is not zero
        if (digits.get(0) == 0) {
            // Swap with the second element
            Collections.swap(digits, 0, 1);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digitMode; i++) {
            sb.append(digits.get(i));
        }

        return sb.toString();
    }
}
