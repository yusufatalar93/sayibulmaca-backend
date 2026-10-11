package com.sayibulmaca.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FriendResponse {
    private UUID friendshipId;
    private UUID userId;
    private String username;
    private String email;
    private String status; // PENDING_SENT, PENDING_RECEIVED, ACCEPTED
    private boolean online;
}
