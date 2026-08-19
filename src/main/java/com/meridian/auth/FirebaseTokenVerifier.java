package com.meridian.auth;

public interface FirebaseTokenVerifier {

    FirebaseUserClaims verify(String idToken);
}
