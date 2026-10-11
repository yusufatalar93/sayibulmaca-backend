import os

backend_dir = "/Users/yusufatalar/dev/number game/backend"

files = {
    "src/main/resources/application.yml": """spring:
  application:
    name: sayibulmaca
  datasource:
    url: jdbc:postgresql://localhost:5432/sayibulmaca
    username: sayibulmaca
    password: sayibulmaca_dev
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

game:
  time-control:
    fast:
      digits-4: 120
      digits-5: 180
    normal:
      digits-4: 240
      digits-5: 360
    long:
      digits-4: 480
      digits-5: 720
  secret-setup-timeout-seconds: 30
  max-guesses-per-player: 30
  first-move-timeout-seconds: 30

firebase:
  credentials-path: ${FIREBASE_CREDENTIALS_PATH:}
  mock-enabled: ${FIREBASE_MOCK_ENABLED:false}
""",
    "src/main/java/com/sayibulmaca/config/FirebaseConfig.java": """package com.sayibulmaca.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    @Value("${firebase.mock-enabled:false}")
    private boolean mockEnabled;

    @Bean
    public FirebaseAuth firebaseAuth() throws IOException {
        if (mockEnabled) {
            return null; // Don't initialize Firebase if mock is enabled
        }
        
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream credentialsStream;
            if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
                credentialsStream = new FileInputStream(credentialsPath);
            } else {
                credentialsStream = getClass().getResourceAsStream("/firebase-service-account.json");
                if (credentialsStream == null) {
                    throw new IOException("Firebase credentials not found. Set FIREBASE_CREDENTIALS_PATH or place firebase-service-account.json in classpath.");
                }
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();

            FirebaseApp.initializeApp(options);
        }
        return FirebaseAuth.getInstance();
    }
}
""",
    "src/main/java/com/sayibulmaca/model/RatingId.java": """package com.sayibulmaca.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RatingId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "digit_mode")
    private short digitMode;
}
""",
    "src/main/java/com/sayibulmaca/model/Rating.java": """package com.sayibulmaca.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {

    @EmbeddedId
    private RatingId id;

    @Column(nullable = false)
    @Builder.Default
    private int rating = 1000;

    @Column(name = "games_played", nullable = false)
    @Builder.Default
    private int gamesPlayed = 0;

    @Column(nullable = false)
    @Builder.Default
    private int wins = 0;

    @Column(nullable = false)
    @Builder.Default
    private int losses = 0;

    @Column(nullable = false)
    @Builder.Default
    private int draws = 0;
}
""",
    "src/main/java/com/sayibulmaca/repository/RatingRepository.java": """package com.sayibulmaca.repository;

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
""",
    "src/main/java/com/sayibulmaca/service/UserService.java": """package com.sayibulmaca.service;

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
""",
    "src/main/java/com/sayibulmaca/security/UserPrincipal.java": """package com.sayibulmaca.security;

import com.sayibulmaca.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;

public class UserPrincipal implements Authentication {

    private final User user;
    private boolean authenticated = true;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return user;
    }

    @Override
    public Object getPrincipal() {
        return user;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }
}
""",
    "src/main/java/com/sayibulmaca/security/FirebaseTokenFilter.java": """package com.sayibulmaca.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.sayibulmaca.model.User;
import com.sayibulmaca.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final FirebaseAuth firebaseAuth;
    private final boolean mockEnabled;

    @Autowired
    public FirebaseTokenFilter(UserService userService, @Autowired(required = false) FirebaseAuth firebaseAuth, @Value("${firebase.mock-enabled:false}") boolean mockEnabled) {
        this.userService = userService;
        this.firebaseAuth = firebaseAuth;
        this.mockEnabled = mockEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                String uid;
                String email;
                
                if (mockEnabled) {
                    uid = token; // use token as uid in mock mode
                    email = token + "@mock.com";
                } else {
                    FirebaseToken firebaseToken = firebaseAuth.verifyIdToken(token);
                    uid = firebaseToken.getUid();
                    email = firebaseToken.getEmail();
                }

                User user = userService.findOrCreateUser(uid, email);
                UserPrincipal principal = new UserPrincipal(user);
                SecurityContextHolder.getContext().setAuthentication(principal);

            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
""",
    "src/main/java/com/sayibulmaca/config/SecurityConfig.java": """package com.sayibulmaca.config;

import com.sayibulmaca.security.FirebaseTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FirebaseTokenFilter firebaseTokenFilter;

    public SecurityConfig(FirebaseTokenFilter firebaseTokenFilter) {
        this.firebaseTokenFilter = firebaseTokenFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/config/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
""",
    "src/main/java/com/sayibulmaca/dto/UserProfileResponse.java": """package com.sayibulmaca.dto;

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
""",
    "src/main/java/com/sayibulmaca/dto/UsernameRequest.java": """package com.sayibulmaca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsernameRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;
}
""",
    "src/main/java/com/sayibulmaca/game/LeagueUtil.java": """package com.sayibulmaca.game;

public class LeagueUtil {

    public static String getLeague(int rating) {
        if (rating < 1000) return "BRONZE";
        if (rating < 1200) return "SILVER";
        if (rating < 1400) return "GOLD";
        if (rating < 1600) return "PLATINUM";
        if (rating < 1800) return "DIAMOND";
        if (rating < 2000) return "MASTER";
        return "LEGEND";
    }

    public static String getTier(int rating) {
        if (rating >= 2000) return null; // Legend has no tier
        
        int offset = rating % 200;
        if (rating < 1000) {
             offset = (rating - 800);
             if(offset < 0) offset = 0;
        }

        if (offset < 50) return "IV";
        if (offset < 100) return "III";
        if (offset < 150) return "II";
        return "I";
    }
    
    public static Integer getProgressToNextTier(int rating) {
        if (rating >= 2000) return null;
        int offset = rating % 200;
         if (rating < 1000) {
             offset = (rating - 800);
             if(offset < 0) offset = 0;
        }
        return offset % 50; // returns 0-49
    }
}
""",
    "src/main/java/com/sayibulmaca/controller/UserController.java": """package com.sayibulmaca.controller;

import com.sayibulmaca.dto.UserProfileResponse;
import com.sayibulmaca.dto.UsernameRequest;
import com.sayibulmaca.game.LeagueUtil;
import com.sayibulmaca.model.Rating;
import com.sayibulmaca.model.User;
import com.sayibulmaca.security.UserPrincipal;
import com.sayibulmaca.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getUserById(principal.getUser().getId());
        List<Rating> ratings = userService.getUserRatings(user.getId());

        List<UserProfileResponse.RatingResponse> ratingResponses = ratings.stream().map(r ->
                UserProfileResponse.RatingResponse.builder()
                        .digitMode(r.getId().getDigitMode())
                        .rating(r.getRating())
                        .league(LeagueUtil.getLeague(r.getRating()))
                        .tier(LeagueUtil.getTier(r.getRating()))
                        .gamesPlayed(r.getGamesPlayed())
                        .wins(r.getWins())
                        .losses(r.getLosses())
                        .draws(r.getDraws())
                        .progressToNextTier(LeagueUtil.getProgressToNextTier(r.getRating()))
                        .build()
        ).collect(Collectors.toList());

        UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .ratings(ratingResponses)
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/username")
    public ResponseEntity<UserProfileResponse> setUsername(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UsernameRequest request) {
        
        userService.setUsername(principal.getUser().getId(), request.getUsername());
        return getMe(principal);
    }
}
"""
}

for path, content in files.items():
    full_path = os.path.join(backend_dir, path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w") as f:
        f.write(content)
