package com.youfuns.webserver;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

public class JwtService {
    private static SecretKey SECRET_KEY = Jwts.SIG.HS256.key().build();
    private static long EXPIRATION_SECONDS = 3600; // 1 hour

    public static void setSecretKey(String secretKey) {
        SECRET_KEY = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public static void setExpiration(long expiration) {
        EXPIRATION_SECONDS = expiration;
    }

    // Generate JWT
    public static String generateToken(String subject) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(EXPIRATION_SECONDS);

        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(SECRET_KEY)
                .compact();
    }

    public static boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractSubject(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload().getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}