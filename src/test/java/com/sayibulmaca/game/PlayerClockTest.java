package com.sayibulmaca.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerClockTest {

    @Test
    void testStartStopAndDeduction() throws InterruptedException {
        PlayerClock clock = new PlayerClock(5000);
        assertEquals(5000, clock.getRemainingMs());

        clock.start();
        Thread.sleep(100);
        clock.stop();

        long remaining = clock.getRemainingMs();
        assertTrue(remaining < 5000, "Time should be deducted");
        assertTrue(remaining > 4000, "Not too much time should be deducted");
    }

    @Test
    void testExpiry() throws InterruptedException {
        PlayerClock clock = new PlayerClock(50);
        clock.start();
        Thread.sleep(100); // More than initial time
        
        assertTrue(clock.isExpired());
        assertEquals(0, clock.getRemainingMs());
        
        clock.stop();
        assertEquals(0, clock.getRemainingMs());
    }
}
