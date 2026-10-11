package com.sayibulmaca.service;

import org.springframework.stereotype.Service;

@Service
public class EloService {

    private static final int INITIAL_K = 40;
    private static final int STANDARD_K = 20;
    private static final int INITIAL_GAME_THRESHOLD = 30;

    public int calculateChange(int myRating, int opponentRating, double score, int gamesPlayed) {
        int k = gamesPlayed < INITIAL_GAME_THRESHOLD ? INITIAL_K : STANDARD_K;
        double expected = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - myRating) / 400.0));
        return (int) Math.round(k * (score - expected));
    }
}
