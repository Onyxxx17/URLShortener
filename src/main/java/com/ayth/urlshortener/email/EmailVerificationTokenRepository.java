package com.ayth.urlshortener.email;

import com.ayth.urlshortener.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByToken(UUID token);

    void deleteAllByUser(User user);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken e WHERE e.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);
}
