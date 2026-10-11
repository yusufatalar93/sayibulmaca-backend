package com.sayibulmaca.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberValidatorTest {

    @Test
    void testValidFourDigits() {
        assertTrue(NumberValidator.isValid("1234", 4));
        assertTrue(NumberValidator.isValid("9876", 4));
        assertTrue(NumberValidator.isValid("1023", 4));
    }

    @Test
    void testValidFiveDigits() {
        assertTrue(NumberValidator.isValid("12345", 5));
        assertTrue(NumberValidator.isValid("98765", 5));
        assertTrue(NumberValidator.isValid("10234", 5));
    }

    @Test
    void testRejectRepeatedDigits() {
        assertFalse(NumberValidator.isValid("1123", 4));
        assertFalse(NumberValidator.isValid("1224", 4));
        assertFalse(NumberValidator.isValid("9879", 4));
    }

    @Test
    void testRejectLeadingZero() {
        assertFalse(NumberValidator.isValid("0123", 4));
        assertFalse(NumberValidator.isValid("0987", 4));
    }

    @Test
    void testRejectWrongLength() {
        assertFalse(NumberValidator.isValid("123", 4));
        assertFalse(NumberValidator.isValid("12345", 4));
        assertFalse(NumberValidator.isValid("1234", 5));
    }

    @Test
    void testRejectNonDigits() {
        assertFalse(NumberValidator.isValid("12a4", 4));
        assertFalse(NumberValidator.isValid("12 4", 4));
        assertFalse(NumberValidator.isValid("abcd", 4));
    }

    @Test
    void testRandomGenerationAlwaysProducesValidNumbers() {
        for (int i = 0; i < 1000; i++) {
            String secret4 = NumberValidator.generateRandomSecret(4);
            assertTrue(NumberValidator.isValid(secret4, 4), "Failed for 4 digits: " + secret4);

            String secret5 = NumberValidator.generateRandomSecret(5);
            assertTrue(NumberValidator.isValid(secret5, 5), "Failed for 5 digits: " + secret5);
        }
    }
}
