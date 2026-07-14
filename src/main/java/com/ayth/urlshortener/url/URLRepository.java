package com.ayth.urlshortener.url;

import com.ayth.urlshortener.users.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface URLRepository extends JpaRepository<URL, Long> {

    Optional<URL> findByOriginalUrl(String originalURL);
    Optional<URL> findByShortCode(String shortURL);
    void deleteByShortCode(String shortCode);
    void deleteByOriginalUrl(String originalURL);
    Optional<URL> findByUserAndOriginalUrl(User user, String originalUrl);
}
