package com.sayibulmaca.security;

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
