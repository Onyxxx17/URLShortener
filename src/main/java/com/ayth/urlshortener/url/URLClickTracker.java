package com.ayth.urlshortener.url;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class URLClickTracker {

    private final URLRepository urlRepository;

    @Autowired
    public URLClickTracker(URLRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Async
    @Transactional
    public void incrementClickCountAndUpdateLastAccessed(String shortCode) {
        urlRepository.findByShortCode(shortCode).ifPresent(url -> {
            url.setClickCount(url.getClickCount() + 1);
            url.setLastAccessedAt(Instant.now());
            urlRepository.save(url);
        });
    }
}
