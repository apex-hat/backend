package com.meridian.auth;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    private static final String APP_NAME = "meridian";
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/cloud-platform");

    private final FirebaseProperties properties;
    private volatile FirebaseAuth firebaseAuth;

    @Override
    public FirebaseUserClaims verify(String idToken) {
        try {
            FirebaseToken token = firebaseAuth().verifyIdToken(idToken);
            return new FirebaseUserClaims(
                    token.getUid(),
                    token.getEmail(),
                    token.getName(),
                    claim(token, "country"),
                    claim(token, "timeZone"),
                    claim(token, "location"),
                    claim(token, "cultureTag")
            );
        } catch (FirebaseAuthException ex) {
            throw new AuthenticationException("Invalid Firebase ID token.", ex);
        }
    }

    private FirebaseAuth firebaseAuth() {
        FirebaseAuth current = firebaseAuth;
        if (current == null) {
            synchronized (this) {
                current = firebaseAuth;
                if (current == null) {
                    current = FirebaseAuth.getInstance(firebaseApp());
                    firebaseAuth = current;
                }
            }
        }
        return current;
    }

    private FirebaseApp firebaseApp() {
        try {
            return FirebaseApp.getInstance(APP_NAME);
        } catch (IllegalStateException ignored) {
            return FirebaseApp.initializeApp(firebaseOptions(), APP_NAME);
        }
    }

    private FirebaseOptions firebaseOptions() {
        if (!StringUtils.hasText(properties.projectId())
                || !StringUtils.hasText(properties.clientEmail())
                || !StringUtils.hasText(properties.privateKey())) {
            throw new IllegalStateException("Firebase service account configuration is missing.");
        }

        try {
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromPkcs8(
                    null,
                    properties.clientEmail(),
                    properties.privateKey().replace("\\n", "\n"),
                    null,
                    SCOPES
            );
            return FirebaseOptions.builder()
                    .setProjectId(properties.projectId())
                    .setCredentials(credentials)
                    .build();
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid Firebase service account configuration.", ex);
        }
    }

    private String claim(FirebaseToken token, String name) {
        Object value = token.getClaims().get(name);
        return value instanceof String text && StringUtils.hasText(text) ? text : null;
    }
}
