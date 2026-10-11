package com.sayibulmaca.model;

import com.sayibulmaca.game.EndReason;
import com.sayibulmaca.game.MatchResult;
import com.sayibulmaca.game.MatchStatus;
import com.sayibulmaca.game.TimeControl;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    private UUID id;

    @Column(name = "digit_mode", nullable = false)
    private short digitMode;

    @Column(name = "time_control", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private TimeControl timeControl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_a_id", nullable = false)
    private User playerA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_b_id", nullable = false)
    private User playerB;

    @Column(name = "secret_a", length = 5)
    private String secretA;

    @Column(name = "secret_b", length = 5)
    private String secretB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MatchStatus status = MatchStatus.SETUP;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private MatchResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 15)
    private EndReason endReason;

    @Column(name = "rating_change_a")
    private Integer ratingChangeA;

    @Column(name = "rating_change_b")
    private Integer ratingChangeB;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
