package com.ayth.urlshortener.config;

import com.ayth.urlshortener.email.EmailVerificationTokenRepository;
import com.ayth.urlshortener.email.PasswordResetTokenRepository;
import com.ayth.urlshortener.url.URLRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupJobService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final URLRepository urlRepository;
    private final com.ayth.urlshortener.url.URLClickEventRepository urlClickEventRepository;

    //  Hourly cleanup for expired tokens.
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("[CRON] Starting cleanup of expired auth tokens");
        Instant now = Instant.now();
        
        int pwdDeleted = passwordResetTokenRepository.deleteExpiredTokens(now);
        int emailDeleted = emailVerificationTokenRepository.deleteExpiredTokens(now);
        
        log.info("[CRON] Deleted {} expired password reset tokens and {} expired email verification tokens", pwdDeleted, emailDeleted);
    }

    //Daily midnight cleanup for expired URLs.
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupExpiredUrls() {
        log.info("[CRON] Starting cleanup of expired URLs");
        Instant now = Instant.now();
        
        // Must delete click events first due to foreign key constraints
        int clicksDeleted = urlClickEventRepository.deleteClickEventsForExpiredUrls(now);
        int urlsDeleted = urlRepository.deleteExpiredUrls(now);
        
        log.info("[CRON] Deleted {} expired URLs and {} associated click events", urlsDeleted, clicksDeleted);
    }
}
