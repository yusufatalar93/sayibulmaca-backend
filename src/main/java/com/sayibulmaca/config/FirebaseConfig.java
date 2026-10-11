package com.sayibulmaca.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${firebase.mock-enabled:false}")
    private boolean mockEnabled;

    @Bean
    public FirebaseAuth firebaseAuth() throws IOException {
        if (mockEnabled) {
            return null;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            InputStream credentialsStream = resolveCredentials();
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            FirebaseApp.initializeApp(options);
        }
        return FirebaseAuth.getInstance();
    }

    private InputStream resolveCredentials() throws IOException {
        // 1) Env var ile JSON içeriği (Railway/Render deploy için)
        String credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON");
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }

        // 2) Dosya yolu ile (lokal geliştirme için)
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            return new FileInputStream(credentialsPath);
        }

        // 3) Classpath'ten (lokal geliştirme alternatifi)
        InputStream stream = getClass().getResourceAsStream("/firebase-service-account.json");
        if (stream != null) {
            return stream;
        }

        throw new IOException(
            "Firebase credentials bulunamadı. " +
            "FIREBASE_CREDENTIALS_JSON env var'ı, FIREBASE_CREDENTIALS_PATH, " +
            "veya firebase-service-account.json dosyasından birini sağlayın."
        );
    }
}
