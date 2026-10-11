package com.sayibulmaca.service;

import com.sayibulmaca.game.TimeControl;
import com.sayibulmaca.model.User;
import com.sayibulmaca.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private static final long CHALLENGE_TIMEOUT_SECONDS = 30;

    private final UserRepository userRepository;
    private final MatchService matchService;
    private final SimpMessagingTemplate messaging;

    private record PendingChallenge(
            UUID id,
            UUID challengerId,
            UUID targetId,
            int digitMode,
            TimeControl timeControl,
            Instant createdAt
    ) {}

    private final Map<UUID, PendingChallenge> pendingChallenges = new ConcurrentHashMap<>();

    public void sendChallenge(UUID challengerId, String targetUsername, int digitMode, String timeControlStr) {
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + targetUsername));

        if (target.getId().equals(challengerId)) {
            throw new IllegalArgumentException("Kendinize meydan okuyamazsınız");
        }

        TimeControl timeControl;
        try {
            timeControl = TimeControl.valueOf(timeControlStr.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Geçersiz zaman kontrolü");
        }

        User challenger = userRepository.findById(challengerId).orElseThrow();

        UUID challengeId = UUID.randomUUID();
        PendingChallenge challenge = new PendingChallenge(
                challengeId, challengerId, target.getId(), digitMode, timeControl, Instant.now()
        );
        pendingChallenges.put(challengeId, challenge);

        messaging.convertAndSendToUser(
                target.getId().toString(),
                "/queue/game",
                Map.of(
                        "type", "CHALLENGE_RECEIVED",
                        "data", Map.of(
                                "challengeId", challengeId.toString(),
                                "challengerUsername", challenger.getUsername() != null
                                        ? challenger.getUsername() : challenger.getEmail(),
                                "digitMode", digitMode,
                                "timeControl", timeControl.name()
                        )
                )
        );

        // Also confirm to challenger
        messaging.convertAndSendToUser(
                challengerId.toString(),
                "/queue/game",
                Map.of(
                        "type", "CHALLENGE_SENT",
                        "data", Map.of(
                                "challengeId", challengeId.toString(),
                                "targetUsername", target.getUsername() != null
                                        ? target.getUsername() : target.getEmail()
                        )
                )
        );
    }

    public void acceptChallenge(UUID userId, UUID challengeId) {
        PendingChallenge challenge = pendingChallenges.remove(challengeId);
        if (challenge == null) {
            messaging.convertAndSendToUser(
                    userId.toString(), "/queue/game",
                    Map.of("type", "CHALLENGE_EXPIRED", "data", Map.of("challengeId", challengeId.toString()))
            );
            return;
        }

        if (!challenge.targetId().equals(userId)) return;

        matchService.createMatch(challenge.challengerId(), challenge.targetId(),
                challenge.digitMode(), challenge.timeControl());
    }

    public void declineChallenge(UUID userId, UUID challengeId) {
        PendingChallenge challenge = pendingChallenges.remove(challengeId);
        if (challenge == null) return;
        if (!challenge.targetId().equals(userId)) return;

        messaging.convertAndSendToUser(
                challenge.challengerId().toString(),
                "/queue/game",
                Map.of(
                        "type", "CHALLENGE_DECLINED",
                        "data", Map.of("challengeId", challengeId.toString())
                )
        );
    }

    @Scheduled(fixedDelay = 10_000)
    public void cleanupExpiredChallenges() {
        Instant cutoff = Instant.now().minusSeconds(CHALLENGE_TIMEOUT_SECONDS);
        pendingChallenges.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                PendingChallenge c = entry.getValue();
                messaging.convertAndSendToUser(
                        c.challengerId().toString(), "/queue/game",
                        Map.of("type", "CHALLENGE_EXPIRED",
                               "data", Map.of("challengeId", c.id().toString()))
                );
                messaging.convertAndSendToUser(
                        c.targetId().toString(), "/queue/game",
                        Map.of("type", "CHALLENGE_EXPIRED",
                               "data", Map.of("challengeId", c.id().toString()))
                );
                return true;
            }
            return false;
        });
    }
}
