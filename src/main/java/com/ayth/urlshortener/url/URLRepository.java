package com.ayth.urlshortener.url;

import com.ayth.urlshortener.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface URLRepository extends JpaRepository<URL, Long> {

    Optional<URL> findByOriginalUrl(String originalURL);
    Optional<URL> findByShortCode(String shortURL);
    void deleteByShortCode(String shortCode);
    void deleteByOriginalUrl(String originalURL);
    Optional<URL> findByUserAndOriginalUrl(User user, String originalUrl);

    @Query(value = "SELECT nextval('url_id_seq')", nativeQuery = true)
    Long getNextId();
}
