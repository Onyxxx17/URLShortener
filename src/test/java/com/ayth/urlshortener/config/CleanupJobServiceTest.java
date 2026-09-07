package com.ayth.urlshortener.config;

import com.ayth.urlshortener.email.EmailVerificationTokenRepository;
import com.ayth.urlshortener.email.PasswordResetTokenRepository;
import com.ayth.urlshortener.url.URLClickEventRepository;
import com.ayth.urlshortener.url.URLRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CleanupJobServiceTest {

    private PasswordResetTokenRepository passwordResetTokenRepository;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private URLRepository urlRepository;
    private URLClickEventRepository urlClickEventRepository;
    private CleanupJobService service;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        urlRepository = mock(URLRepository.class);
        urlClickEventRepository = mock(URLClickEventRepository.class);
        service = new CleanupJobService(
                passwordResetTokenRepository, emailVerificationTokenRepository, urlRepository, urlClickEventRepository);
    }

    @Test
    void cleanupExpiredTokens_purgesBothPasswordResetAndEmailVerificationTokens() {
        when(passwordResetTokenRepository.deleteExpiredTokens(any())).thenReturn(3);
        when(emailVerificationTokenRepository.deleteExpiredTokens(any())).thenReturn(5);

        service.cleanupExpiredTokens();

        verify(passwordResetTokenRepository).deleteExpiredTokens(any(Instant.class));
        verify(emailVerificationTokenRepository).deleteExpiredTokens(any(Instant.class));
        verifyNoInteractions(urlRepository, urlClickEventRepository);
    }

    @Test
    void cleanupExpiredUrls_deletesClickEventsBeforeUrls_toSatisfyForeignKeyConstraint() {
        when(urlClickEventRepository.deleteClickEventsForExpiredUrls(any())).thenReturn(10);
        when(urlRepository.deleteExpiredUrls(any())).thenReturn(2);

        service.cleanupExpiredUrls();

        InOrder order = inOrder(urlClickEventRepository, urlRepository);
        order.verify(urlClickEventRepository).deleteClickEventsForExpiredUrls(any(Instant.class));
        order.verify(urlRepository).deleteExpiredUrls(any(Instant.class));
        verifyNoInteractions(passwordResetTokenRepository, emailVerificationTokenRepository);
    }
}
