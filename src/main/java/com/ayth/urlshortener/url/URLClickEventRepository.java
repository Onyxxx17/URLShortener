package com.ayth.urlshortener.url;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface URLClickEventRepository extends JpaRepository<URLClickEvent, UUID> {
    List<URLClickEvent> findByUrlOrderByClickTimestampDesc(URL url);

    @Modifying
    @Query("DELETE FROM URLClickEvent e WHERE e.url.id IN (SELECT u.id FROM URL u WHERE u.expiresAt < :now)")
    int deleteClickEventsForExpiredUrls(@Param("now") Instant now);
}
