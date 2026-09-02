package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests — no Spring context. JwtService's @Value fields are set
 * directly via reflection, using the same test secret as
 * src/test/resources/application.properties.
 */
class JwtServiceTest {

    private static final String SECRET = "dGhpcyBpcyBhIHZlcnkgc2VjcmV0IGp3dCBzaWduaW5nIGtleQ==";
    private static final long EXPIRATION_MS = 900_000L; // 15 minutes

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);
    }

    private UserPrincipal principalFor(String email, String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setUsername(username);
        return new UserPrincipal(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Generation + round-trip
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generateToken_producesTokenThatIsValidAndCarriesTheUsersEmail() {
        UserPrincipal principal = principalFor("alice@example.com", "alice");

        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void generateToken_embedsUserIdAndUsernameAsClaims() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("bob@example.com");
        user.setUsername("bob");
        UserPrincipal principal = new UserPrincipal(user);

        String token = jwtService.generateToken(principal);
        Claims claims = jwtService.extractAllClaims(token);

        assertThat(claims.get("userId", String.class)).isEqualTo(user.getId().toString());
        assertThat(claims.get("username", String.class)).isEqualTo("bob");
    }

    @Test
    void generateToken_setsExpirationConsistentWithConfiguredExpirationMs() {
        String token = jwtService.generateToken(principalFor("carol@example.com", "carol"));

        Claims claims = jwtService.extractAllClaims(token);
        long actualLifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertThat(actualLifetimeMs).isEqualTo(EXPIRATION_MS);
    }

    @Test
    void generateToken_twoDifferentUsers_produceTokensThatResolveToTheirOwnEmail() {
        String tokenA = jwtService.generateToken(principalFor("a@example.com", "userA"));
        String tokenB = jwtService.generateToken(principalFor("b@example.com", "userB"));

        assertThat(tokenA).isNotEqualTo(tokenB);
        assertThat(jwtService.extractEmail(tokenA)).isEqualTo("a@example.com");
        assertThat(jwtService.extractEmail(tokenB)).isEqualTo("b@example.com");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation failures
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Date past = new Date(System.currentTimeMillis() - 60_000);
        String expiredToken = Jwts.builder()
                .subject("expired@example.com")
                .issuedAt(new Date(past.getTime() - EXPIRATION_MS))
                .expiration(past)
                .signWith(key)
                .compact();

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    void isTokenValid_signedWithADifferentKey_returnsFalse() {
        // A well-formed, unexpired token — but not signed with this service's key.
        SecretKey otherKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode("YW5vdGhlciB2ZXJ5IGRpZmZlcmVudCBzZWNyZXQga2V5ISE="));
        String token = Jwts.builder()
                .subject("forged@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(otherKey)
                .compact();

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_tamperedPayload_returnsFalse() {
        String token = jwtService.generateToken(principalFor("dave@example.com", "dave"));
        String[] parts = token.split("\\.");

        // Flip the payload segment without re-signing — the signature no
        // longer matches, exactly what an attacker gets from editing a decoded
        // JWT by hand.
        String tamperedPayload = new StringBuilder(parts[1]).reverse().toString();
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void isTokenValid_emptyToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }
}
