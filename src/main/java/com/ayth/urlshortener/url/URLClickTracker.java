package com.ayth.urlshortener.url;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class URLClickTracker {
    public static volatile String lastExecutionThreadName;

    private final URLRepository urlRepository;

    @Autowired
    public URLClickTracker(URLRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Async
    @Transactional
    public void incrementClickCountAndUpdateLastAccessed(String shortCode) {
        lastExecutionThreadName = Thread.currentThread().getName();
        urlRepository.findByShortCode(shortCode).ifPresent(url -> {
            url.setClickCount(url.getClickCount() + 1);
            url.setLastAccessedAt(Instant.now());
            urlRepository.save(url);
        });
    }
}
