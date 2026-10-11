package com.sayibulmaca.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnlineUserService {

    private final Set<UUID> onlineUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void userConnected(UUID userId) {
        onlineUsers.add(userId);
    }

    public void userDisconnected(UUID userId) {
        onlineUsers.remove(userId);
    }

    public boolean isOnline(UUID userId) {
        return onlineUsers.contains(userId);
    }

    public Set<UUID> getOnlineUsers() {
        return Collections.unmodifiableSet(onlineUsers);
    }
}
