package com.sayibulmaca.service;

import com.sayibulmaca.model.Rating;
import com.sayibulmaca.model.RatingId;
import com.sayibulmaca.model.User;
import com.sayibulmaca.repository.RatingRepository;
import com.sayibulmaca.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RatingRepository ratingRepository;

    public UserService(UserRepository userRepository, RatingRepository ratingRepository) {
        this.userRepository = userRepository;
        this.ratingRepository = ratingRepository;
    }

    @Transactional
    public User findOrCreateUser(String firebaseUid, String email) {
        return userRepository.findByFirebaseUid(firebaseUid).orElseGet(() -> {
            User newUser = User.builder()
                    .firebaseUid(firebaseUid)
                    .email(email)
                    .build();
            User savedUser = userRepository.save(newUser);

            // Create initial ratings
            Rating rating4 = Rating.builder()
                    .id(new RatingId(savedUser.getId(), (short) 4))
                    .build();
            Rating rating5 = Rating.builder()
                    .id(new RatingId(savedUser.getId(), (short) 5))
                    .build();
            ratingRepository.saveAll(List.of(rating4, rating5));

            return savedUser;
        });
    }

    @Transactional
    public User setUsername(UUID userId, String username) {
        if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Invalid username format");
        }
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent() && !existing.get().getId().equals(userId)) {
            throw new IllegalArgumentException("Username already taken");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setUsername(username);
        return userRepository.save(user);
    }
    
    public User getUserById(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public List<Rating> getUserRatings(UUID userId) {
        return ratingRepository.findByIdUserId(userId);
    }
}
