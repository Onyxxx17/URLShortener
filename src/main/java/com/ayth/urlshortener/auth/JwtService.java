package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/*
Tokens are signed with HS256 and embed the user's UUID and email as
claims. The secret and expiry are read from {@code application.properties}.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // ── Key ──────────────────────────────────────────────────────────────────

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    // ── Token generation ─────────────────────────────────────────────────────

    public String generateToken(UserPrincipal principal) {
        User user = principal.getUser();
        Date now = new Date();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey())
                .compact();
    }

    // ── Claims extraction ────────────────────────────────────────────────────

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    // ── Validation ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the token can be parsed, is signed correctly,
     * and has not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token); // throws on invalid/expired
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
