package com.springqprobackend.springqpro.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    // Field(s):
    private SecretKey key;
    private final long accessTokenExpirationMs = 15 * 60 * 1000; // 15 min
    private final long refreshTokenExpirationMs = 7 * 24 * 60 * 60 * 1000; // 7 days

    // Constructor(s):
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Method(s):
    // SHORT-LIVED TOKEN:
    public String generateAccessToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(key)
                .compact();
    }
    // REFRESH TOKEN = LONG-LIVED TOKEN:
    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(key)
                .compact();
    }
    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
    public boolean isExpired(String token) {
        try {
            Date expDate = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
            return expDate.before(new Date());
        } catch (Exception e) {
            return true;    // Invalid tokens are expired.
        }
    }
    /* NOTE: This method below can definitely just combine isExpired and extractEmail but better to make it be a "single parse"
    so I completely eliminate the possibility of signature tampering, corrupt tokens, and so on. */
    /* Stuff that could go wrong apparently according to ChatGPT (if I just did isExpired -> extractEmail):
    Invalid signature, Tampered or truncated token, Wrong key, Wrong algorithm, Null token, Missing subject
    Expired token, and Other malformed JWT errors */
    public String validateAndGetSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // Check expiration manually (JJWT 0.12 no longer auto-fails expired tokens)
            if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
                throw new ExpiredJwtException(null, claims, "Token is expired");
            }
            return claims.getSubject();
        } catch (ExpiredJwtException ex) {
            throw ex; // bubble up "expired" explicitly
        } catch (JwtException ex) {
            // SignatureException, MalformedJwtException, UnsupportedJwtException, etc.
            throw new JwtException("Invalid JWT token", ex);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("JWT token is empty or null", ex);
        }
    }

}
