package com.sayibulmaca.service;

import com.sayibulmaca.dto.FriendResponse;
import com.sayibulmaca.model.Friendship;
import com.sayibulmaca.model.User;
import com.sayibulmaca.repository.FriendshipRepository;
import com.sayibulmaca.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messaging;
    private final OnlineUserService onlineUserService;

    @Transactional
    public FriendResponse sendRequest(UUID requesterId, String usernameOrEmail) {
        User addressee = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));

        if (addressee.getId().equals(requesterId)) {
            throw new IllegalArgumentException("Kendinize arkadaşlık isteği gönderemezsiniz");
        }

        Optional<Friendship> existing = friendshipRepository.findBetween(requesterId, addressee.getId());
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (f.getStatus() == Friendship.Status.ACCEPTED) {
                throw new IllegalArgumentException("Zaten arkadaşsınız");
            }
            if (f.getStatus() == Friendship.Status.PENDING) {
                throw new IllegalArgumentException("Zaten bekleyen bir istek var");
            }
            // DECLINED → re-send
            f.setStatus(Friendship.Status.PENDING);
            f.setRequester(userRepository.findById(requesterId).orElseThrow());
            f.setAddressee(addressee);
            friendshipRepository.save(f);
            notifyFriendRequest(addressee.getId(), requesterId, f.getId());
            return toResponse(f, requesterId);
        }

        User requester = userRepository.findById(requesterId).orElseThrow();
        Friendship friendship = Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(Friendship.Status.PENDING)
                .build();
        friendship = friendshipRepository.save(friendship);

        notifyFriendRequest(addressee.getId(), requesterId, friendship.getId());
        return toResponse(friendship, requesterId);
    }

    @Transactional
    public FriendResponse acceptRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("İstek bulunamadı"));

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalArgumentException("Bu isteği kabul etme yetkiniz yok");
        }
        if (friendship.getStatus() != Friendship.Status.PENDING) {
            throw new IllegalArgumentException("İstek zaten işlenmiş");
        }

        friendship.setStatus(Friendship.Status.ACCEPTED);
        friendship = friendshipRepository.save(friendship);

        // Notify requester
        messaging.convertAndSendToUser(
                friendship.getRequester().getId().toString(),
                "/queue/game",
                java.util.Map.of(
                        "type", "FRIEND_REQUEST_ACCEPTED",
                        "data", java.util.Map.of(
                                "friendshipId", friendship.getId().toString(),
                                "userId", userId.toString(),
                                "username", friendship.getAddressee().getUsername() != null
                                        ? friendship.getAddressee().getUsername()
                                        : friendship.getAddressee().getEmail()
                        )
                )
        );

        return toResponse(friendship, userId);
    }

    @Transactional
    public void declineRequest(UUID userId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("İstek bulunamadı"));

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalArgumentException("Bu isteği reddetme yetkiniz yok");
        }

        friendship.setStatus(Friendship.Status.DECLINED);
        friendshipRepository.save(friendship);
    }

    @Transactional
    public void removeFriend(UUID userId, UUID friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Arkadaşlık bulunamadı"));

        if (!friendship.getRequester().getId().equals(userId) &&
            !friendship.getAddressee().getId().equals(userId)) {
            throw new IllegalArgumentException("Bu arkadaşlığı silme yetkiniz yok");
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getFriends(UUID userId) {
        return friendshipRepository.findAcceptedFriendships(userId).stream()
                .map(f -> toResponse(f, userId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getPendingRequests(UUID userId) {
        return friendshipRepository.findPendingRequests(userId).stream()
                .map(f -> toResponse(f, userId))
                .collect(Collectors.toList());
    }

    private void notifyFriendRequest(UUID addresseeId, UUID requesterId, UUID friendshipId) {
        User requester = userRepository.findById(requesterId).orElseThrow();
        messaging.convertAndSendToUser(
                addresseeId.toString(),
                "/queue/game",
                java.util.Map.of(
                        "type", "FRIEND_REQUEST_RECEIVED",
                        "data", java.util.Map.of(
                                "friendshipId", friendshipId.toString(),
                                "userId", requesterId.toString(),
                                "username", requester.getUsername() != null
                                        ? requester.getUsername() : requester.getEmail()
                        )
                )
        );
    }

    private FriendResponse toResponse(Friendship f, UUID currentUserId) {
        boolean iAmRequester = f.getRequester().getId().equals(currentUserId);
        User friend = iAmRequester ? f.getAddressee() : f.getRequester();

        String status = switch (f.getStatus()) {
            case ACCEPTED -> "ACCEPTED";
            case PENDING -> iAmRequester ? "PENDING_SENT" : "PENDING_RECEIVED";
            case DECLINED -> "DECLINED";
        };

        return FriendResponse.builder()
                .friendshipId(f.getId())
                .userId(friend.getId())
                .username(friend.getUsername() != null ? friend.getUsername() : friend.getEmail())
                .email(friend.getEmail())
                .status(status)
                .online(onlineUserService.isOnline(friend.getId()))
                .build();
    }
}
