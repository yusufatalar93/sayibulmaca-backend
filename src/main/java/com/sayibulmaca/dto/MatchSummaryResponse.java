package com.sayibulmaca.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MatchSummaryResponse {
    private UUID matchId;
    private String opponentUsername;
    private String myResult;
    private Integer myRatingChange;
    private int digitMode;
    private String timeControl;
    private LocalDateTime finishedAt;
}
