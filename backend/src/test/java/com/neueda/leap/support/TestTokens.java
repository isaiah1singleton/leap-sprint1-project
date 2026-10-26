package com.neueda.leap.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Signs test JWTs through the same HS256 contract as the NestJS auth service. */
public final class TestTokens {
    public static final String SECRET = "leap-test-signing-secret-at-least-32-bytes-long";
    private TestTokens() { }

    public static String bearer(String email, String... roles) {
        return "Bearer " + token(email, Instant.now().plusSeconds(900), SECRET, roles);
    }

    public static String token(String email, Instant expires, String secret, String... roles) {
        var claims = new JWTClaimsSet.Builder().subject(email).claim("name", "Test Trader")
                .claim("roles", List.of(roles)).issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expires)).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
