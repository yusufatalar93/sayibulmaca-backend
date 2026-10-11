package com.sayibulmaca.repository;

import com.sayibulmaca.model.Friendship;
import com.sayibulmaca.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findAcceptedFriendships(@Param("userId") UUID userId);

    @Query("SELECT f FROM Friendship f WHERE f.addressee.id = :userId AND f.status = 'PENDING'")
    List<Friendship> findPendingRequests(@Param("userId") UUID userId);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :a AND f.addressee.id = :b) OR (f.requester.id = :b AND f.addressee.id = :a)")
    Optional<Friendship> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f WHERE " +
           "((f.requester.id = :a AND f.addressee.id = :b) OR (f.requester.id = :b AND f.addressee.id = :a)) " +
           "AND f.status = 'ACCEPTED'")
    boolean areFriends(@Param("a") UUID a, @Param("b") UUID b);
}
