package com.ayth.urlshortener.url;

import com.ayth.urlshortener.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface URLRepository extends JpaRepository<URL, Long> {

    Optional<URL> findByOriginalUrl(String originalURL);
    Optional<URL> findByShortCode(String shortURL);
    void deleteByShortCode(String shortCode);
    void deleteByOriginalUrl(String originalURL);
    Optional<URL> findByUserAndOriginalUrl(User user, String originalUrl);
    List<URL> findByUserOrderByCreatedAtDesc(User user);

    @Modifying
    @Query("DELETE FROM URL u WHERE u.expiresAt < :now")
    int deleteExpiredUrls(@Param("now") Instant now);

}
