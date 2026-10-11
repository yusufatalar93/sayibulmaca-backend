package com.sayibulmaca.service;

import com.sayibulmaca.game.TimeControl;
import com.sayibulmaca.model.Rating;
import com.sayibulmaca.model.RatingId;
import com.sayibulmaca.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MatchmakingQueue {

    private final MatchService matchService;
    private final RatingRepository ratingRepository;

    public record QueueEntry(UUID userId, int digitMode, TimeControl timeControl, int rating, Instant joinedAt) {}

    private final Map<String, List<QueueEntry>> queues = new ConcurrentHashMap<>();

    private String key(int digitMode, TimeControl timeControl) {
        return digitMode + "_" + timeControl.name();
    }

    public synchronized boolean addToQueue(UUID userId, int digitMode, TimeControl timeControl) {
        if (isInQueue(userId)) return false;
        if (matchService.getActiveMatchId(userId).isPresent()) return false;

        Rating rating = ratingRepository.findById(new RatingId(userId, (short) digitMode)).orElse(null);
        int currentRating = rating != null ? rating.getRating() : 1000;

        String k = key(digitMode, timeControl);
        queues.computeIfAbsent(k, x -> new ArrayList<>())
              .add(new QueueEntry(userId, digitMode, timeControl, currentRating, Instant.now()));
        return true;
    }

    public synchronized boolean removeFromQueue(UUID userId) {
        for (List<QueueEntry> queue : queues.values()) {
            if (queue.removeIf(e -> e.userId().equals(userId))) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isInQueue(UUID userId) {
        return queues.values().stream().anyMatch(q -> q.stream().anyMatch(e -> e.userId().equals(userId)));
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void runMatchmaking() {
        for (Map.Entry<String, List<QueueEntry>> entry : queues.entrySet()) {
            List<QueueEntry> queue = entry.getValue();
            if (queue.size() < 2) {
                sendQueueStatusToAll(queue);
                continue;
            }

            Set<UUID> matched = new HashSet<>();

            for (int i = 0; i < queue.size(); i++) {
                QueueEntry a = queue.get(i);
                if (matched.contains(a.userId())) continue;

                long waitA = Duration.between(a.joinedAt(), Instant.now()).toSeconds();
                int rangeA = Math.min(50 + (int) (waitA / 5) * 50, 400);
                boolean anyA = waitA >= 60;

                for (int j = i + 1; j < queue.size(); j++) {
                    QueueEntry b = queue.get(j);
                    if (matched.contains(b.userId())) continue;

                    long waitB = Duration.between(b.joinedAt(), Instant.now()).toSeconds();
                    int rangeB = Math.min(50 + (int) (waitB / 5) * 50, 400);
                    boolean anyB = waitB >= 60;

                    int ratingDiff = Math.abs(a.rating() - b.rating());
                    int effectiveRange = Math.max(rangeA, rangeB);

                    if (anyA || anyB || ratingDiff <= effectiveRange) {
                        matched.add(a.userId());
                        matched.add(b.userId());
                        matchService.createMatch(a.userId(), b.userId(), a.digitMode(), a.timeControl());
                        break;
                    }
                }
            }

            queue.removeIf(e -> matched.contains(e.userId()));

            // Send QUEUE_STATUS to remaining players
            sendQueueStatusToAll(queue);
        }
    }

    private void sendQueueStatusToAll(List<QueueEntry> queue) {
        for (QueueEntry entry : queue) {
            long waitSeconds = Duration.between(entry.joinedAt(), Instant.now()).toSeconds();
            int currentRange = Math.min(50 + (int) (waitSeconds / 5) * 50, 400);
            matchService.sendQueueStatus(entry.userId(), waitSeconds, currentRange);
        }
    }
}
