package com.sayibulmaca.repository;

import com.sayibulmaca.model.Guess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GuessRepository extends JpaRepository<Guess, UUID> {

    @Query("SELECT g FROM Guess g WHERE g.match.id = :matchId ORDER BY g.createdAt ASC")
    List<Guess> findByMatchIdOrderByCreatedAt(@Param("matchId") UUID matchId);
}
