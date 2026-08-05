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
    private final URLClickEventRepository urlClickEventRepository;

    @Autowired
    public URLClickTracker(URLRepository urlRepository, URLClickEventRepository urlClickEventRepository) {
        this.urlRepository = urlRepository;
        this.urlClickEventRepository = urlClickEventRepository;
    }

    @Async
    @Transactional
    public void incrementClickCountAndUpdateLastAccessed(String shortCode, String referer, String userAgent) {
        lastExecutionThreadName = Thread.currentThread().getName();      
        urlRepository.findByShortCode(shortCode).ifPresent(url -> {
            url.setClickCount(url.getClickCount() + 1);
            url.setLastAccessedAt(Instant.now());
            urlRepository.save(url);

            URLClickEvent event = URLClickEvent.builder()
                    .url(url)
                    .clickTimestamp(Instant.now())
                    .referer(referer)
                    .userAgent(userAgent)
                    .build();
            urlClickEventRepository.save(event);
        });
    }
}
