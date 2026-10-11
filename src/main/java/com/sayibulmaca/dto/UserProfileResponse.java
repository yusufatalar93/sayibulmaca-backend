package com.sayibulmaca.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private LocalDateTime createdAt;
    private List<RatingResponse> ratings;
    private UUID activeMatchId;
    private String activeMatchPhase;

    @Data
    @Builder
    public static class RatingResponse {
        private short digitMode;
        private int rating;
        private String league;
        private String tier;
        private int gamesPlayed;
        private int wins;
        private int losses;
        private int draws;
        private Integer progressToNextTier;
    }
}
