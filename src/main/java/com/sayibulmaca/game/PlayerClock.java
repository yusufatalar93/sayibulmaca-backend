package com.sayibulmaca.game;

/**
 * Represents a chess clock for a single player in a match.
 * Provides thread-safe operations to manage remaining time.
 */
public class PlayerClock {
    private long remainingMs;
    private Long lastStartedAt;

    /**
     * Constructs a new PlayerClock with the initial time.
     * @param initialMs initial time in milliseconds
     */
    public PlayerClock(long initialMs) {
        this.remainingMs = initialMs;
        this.lastStartedAt = null;
    }

    /**
     * Starts the clock.
     */
    public synchronized void start() {
        if (lastStartedAt == null) {
            lastStartedAt = System.currentTimeMillis();
        }
    }

    /**
     * Stops the clock and deducts the elapsed time from the remaining time.
     */
    public synchronized void stop() {
        if (lastStartedAt != null) {
            long elapsed = System.currentTimeMillis() - lastStartedAt;
            remainingMs -= elapsed;
            if (remainingMs < 0) {
                remainingMs = 0;
            }
            lastStartedAt = null;
        }
    }

    /**
     * Gets the current remaining time, accounting for any currently running time.
     * @return the remaining time in milliseconds
     */
    public synchronized long getRemainingMs() {
        if (lastStartedAt != null) {
            long elapsed = System.currentTimeMillis() - lastStartedAt;
            long currentRemaining = remainingMs - elapsed;
            return Math.max(0, currentRemaining);
        }
        return remainingMs;
    }

    /**
     * Checks if the time has run out.
     * @return true if the clock is expired, false otherwise
     */
    public synchronized boolean isExpired() {
        return getRemainingMs() == 0;
    }
}
