package com.sayibulmaca.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MatchDetailResponse {
    private UUID matchId;
    private String playerAUsername;
    private String playerBUsername;
    private int digitMode;
    private String timeControl;
    private String secretA;
    private String secretB;
    private String status;
    private String result;
    private String endReason;
    private Integer ratingChangeA;
    private Integer ratingChangeB;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<GuessDto> guesses;

    @Data
    @Builder
    public static class GuessDto {
        private UUID playerId;
        private int guessNumber;
        private String value;
        private int plus;
        private int minus;
        private int timeLeftMs;
        private LocalDateTime createdAt;
    }
}
