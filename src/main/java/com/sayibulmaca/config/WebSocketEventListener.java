package com.sayibulmaca.config;

import com.sayibulmaca.security.UserPrincipal;
import com.sayibulmaca.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final OnlineUserService onlineUserService;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user instanceof UserPrincipal up) {
            onlineUserService.userConnected(up.getUser().getId());
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        if (user instanceof UserPrincipal up) {
            onlineUserService.userDisconnected(up.getUser().getId());
        }
    }
}
