package com.sayibulmaca.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.sayibulmaca.model.User;
import com.sayibulmaca.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final UserService userService;
    private final FirebaseAuth firebaseAuth;
    private final boolean mockEnabled;

    @Autowired
    public WebSocketAuthInterceptor(UserService userService,
                                    @Autowired(required = false) FirebaseAuth firebaseAuth,
                                    @Value("${firebase.mock-enabled:false}") boolean mockEnabled) {
        this.userService = userService;
        this.firebaseAuth = firebaseAuth;
        this.mockEnabled = mockEnabled;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return message;
        }

        String token = authHeader.substring(7);
        try {
            String uid;
            String email;
            if (mockEnabled) {
                uid = token;
                email = token + "@mock.com";
            } else {
                FirebaseToken firebaseToken = firebaseAuth.verifyIdToken(token);
                uid = firebaseToken.getUid();
                email = firebaseToken.getEmail();
            }
            User user = userService.findOrCreateUser(uid, email);
            accessor.setUser(new UserPrincipal(user));
        } catch (Exception e) {
            // Leave user unset — controller will reject unauthenticated messages
        }

        return message;
    }
}
