package com.sayibulmaca.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guess {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @Column(name = "guess_number", nullable = false)
    private int guessNumber;

    @Column(nullable = false, length = 5)
    private String value;

    @Column(nullable = false)
    private short plus;

    @Column(nullable = false)
    private short minus;

    @Column(name = "time_left_ms", nullable = false)
    private int timeLeftMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
