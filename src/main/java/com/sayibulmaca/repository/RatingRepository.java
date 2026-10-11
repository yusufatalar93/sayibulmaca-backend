package com.sayibulmaca.repository;

import com.sayibulmaca.model.Rating;
import com.sayibulmaca.model.RatingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RatingRepository extends JpaRepository<Rating, RatingId> {
    List<Rating> findByIdUserId(UUID userId);
}
