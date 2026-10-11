package com.sayibulmaca.repository;

import com.sayibulmaca.model.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Query("SELECT m FROM Match m WHERE (m.playerA.id = :userId OR m.playerB.id = :userId) " +
           "AND m.status IN (com.sayibulmaca.game.MatchStatus.FINISHED, com.sayibulmaca.game.MatchStatus.ABORTED) " +
           "ORDER BY m.finishedAt DESC")
    Page<Match> findFinishedByPlayer(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT m FROM Match m LEFT JOIN FETCH m.playerA LEFT JOIN FETCH m.playerB WHERE m.id = :id")
    java.util.Optional<Match> findByIdWithPlayers(@Param("id") UUID id);
}
