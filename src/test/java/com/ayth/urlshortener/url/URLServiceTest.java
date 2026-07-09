package com.ayth.urlshortener.url;

import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import com.ayth.urlshortener.exception.UrlAlreadyExistsException;
import com.ayth.urlshortener.exception.UrlExpiredException;
import com.ayth.urlshortener.exception.UrlNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class URLServiceTest {

    @Autowired
    private URLService urlService;

    @Autowired
    private URLRepository urlRepository;

    private static final String TEST_SHORT_CODE = "testShort";
    private static final String TEST_ORIGINAL_URL = "https://example-test.com";

    @BeforeEach
    void setUp() {
        URLClickTracker.lastExecutionThreadName = null;
        cleanupTestDb();
    }

    private void cleanupTestDb() {
        urlRepository.findByShortCode("testAsync").ifPresent(urlRepository::delete);
        urlRepository.findByShortCode(TEST_SHORT_CODE).ifPresent(urlRepository::delete);
        urlRepository.findByOriginalUrl(TEST_ORIGINAL_URL).ifPresent(urlRepository::delete);
        urlRepository.findByOriginalUrl("https://already-exists.com").ifPresent(urlRepository::delete);
        urlRepository.findByShortCode("exist123").ifPresent(urlRepository::delete);
    }

    // ==========================================
    // Async Click Tracking & Redirect Tests
    // ==========================================
    @Test
    void getUrlForRedirect_ShouldIncrementClickCountAsynchronously() throws InterruptedException {
        String shortCode = "testAsync";
        URL url = new URL();
        url.setOriginalUrl("https://example.com");
        url.setShortCode(shortCode);
        url.setCreatedAt(Instant.now());
        url.setClickCount(0);
        urlRepository.save(url);

        String callingThreadName = Thread.currentThread().getName();

        try {
            String redirectUrl = urlService.getUrlForRedirect(shortCode);
            assertEquals("https://example.com", redirectUrl);

            // Wait for the async task to execute (max 5 seconds)
            boolean executed = false;
            for (int i = 0; i < 50; i++) {
                if (URLClickTracker.lastExecutionThreadName != null) {
                    executed = true;
                    break;
                }
                Thread.sleep(100);
            }
            assertTrue(executed, "The async click tracking task was not executed within the timeout");
            assertNotEquals(callingThreadName, URLClickTracker.lastExecutionThreadName);

            // Verify db count updated
            URL updatedUrl = urlRepository.findByShortCode(shortCode).orElseThrow();
            assertEquals(1, updatedUrl.getClickCount());
            assertNotNull(updatedUrl.getLastAccessedAt());
        } finally {
            urlRepository.findByShortCode(shortCode).ifPresent(urlRepository::delete);
        }
    }

    @Test
    void getUrlForRedirect_ShouldThrowUrlExpiredException_WhenExpired() {
        URL url = new URL();
        url.setOriginalUrl(TEST_ORIGINAL_URL);
        url.setShortCode(TEST_SHORT_CODE);
        url.setCreatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        url.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS)); // Past expiration
        urlRepository.save(url);

        try {
            assertThrows(UrlExpiredException.class, () -> urlService.getUrlForRedirect(TEST_SHORT_CODE));
        } finally {
            urlRepository.delete(url);
        }
    }

    // ==========================================
    // Find URL Tests
    // ==========================================
    @Test
    void findByShortURL_ShouldReturnUrl_WhenExists() {
        URL url = new URL();
        url.setOriginalUrl(TEST_ORIGINAL_URL);
        url.setShortCode(TEST_SHORT_CODE);
        urlRepository.save(url);

        try {
            URL found = urlService.findByShortURL(TEST_SHORT_CODE);
            assertNotNull(found);
            assertEquals(TEST_ORIGINAL_URL, found.getOriginalUrl());
        } finally {
            urlRepository.delete(url);
        }
    }

    @Test
    void findByShortURL_ShouldThrowUrlNotFoundException_WhenNotExists() {
        assertThrows(UrlNotFoundException.class, () -> urlService.findByShortURL("nonExistentCode"));
    }

    // ==========================================
    // Create URL Tests
    // ==========================================
    @Test
    void createUrl_ShouldCreateAndSaveUrl_WhenNewUrl() {
        try {
            URL created = urlService.createUrl(TEST_ORIGINAL_URL);
            assertNotNull(created);
            assertNotNull(created.getShortCode());
            assertEquals(TEST_ORIGINAL_URL, created.getOriginalUrl());
            assertEquals(0, created.getClickCount());
            assertNotNull(created.getExpiresAt());

            Optional<URL> saved = urlRepository.findByOriginalUrl(TEST_ORIGINAL_URL);
            assertTrue(saved.isPresent());
        } finally {
            urlRepository.findByOriginalUrl(TEST_ORIGINAL_URL).ifPresent(urlRepository::delete);
        }
    }

    @Test
    void createUrl_ShouldThrowUrlAlreadyExistsException_WhenUrlAlreadyExists() {
        URL url = new URL();
        url.setOriginalUrl("https://already-exists.com");
        url.setShortCode("exist123");
        url.setCreatedAt(Instant.now());
        urlRepository.save(url);

        try {
            assertThrows(UrlAlreadyExistsException.class, () -> urlService.createUrl("https://already-exists.com"));
        } finally {
            urlRepository.delete(url);
        }
    }

    @Test
    void createUrlWithResponse_ShouldReturnCreateUrlResponse() {
        try {
            CreateUrlResponse response = urlService.createUrlWithResponse(TEST_ORIGINAL_URL, "http://localhost:8080");
            assertNotNull(response);
            assertNotNull(response.getShortCode());
            assertTrue(response.getShortUrl().startsWith("http://localhost:8080/"));
            assertEquals(TEST_ORIGINAL_URL, response.getOriginalUrl());
        } finally {
            urlRepository.findByOriginalUrl(TEST_ORIGINAL_URL).ifPresent(urlRepository::delete);
        }
    }

    // ==========================================
    // Statistics Response Tests
    // ==========================================
    @Test
    void createUrlStatsResponse_ShouldReturnStatsResponseAndUpdateLastAccessed() {
        URL url = new URL();
        url.setOriginalUrl(TEST_ORIGINAL_URL);
        url.setShortCode(TEST_SHORT_CODE);
        url.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        url.setExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));
        urlRepository.save(url);

        try {
            StatsResponse response = urlService.createUrlStatsResponse(TEST_SHORT_CODE);
            assertNotNull(response);
            assertEquals(TEST_SHORT_CODE, response.getShortCode());
            assertEquals(TEST_ORIGINAL_URL, response.getOriginalUrl());
            assertNotNull(response.getLastAccessedAt());
            assertTrue(response.getDaysUntilExpiry() >= 4);
            assertTrue(response.getAgeInDays() >= 2);
            assertFalse(response.getIsExpired());
        } finally {
            urlRepository.delete(url);
        }
    }

    // ==========================================
    // Delete URL Tests
    // ==========================================
    @Test
    void deleteById_ShouldDeleteUrl_WhenExists() {
        URL url = new URL();
        url.setOriginalUrl(TEST_ORIGINAL_URL);
        url.setShortCode(TEST_SHORT_CODE);
        URL saved = urlRepository.save(url);

        urlService.deleteById(saved.getId());
        assertTrue(urlRepository.findById(saved.getId()).isEmpty());
    }

    @Test
    void deleteById_ShouldThrowUrlNotFoundException_WhenNotExists() {
        assertThrows(UrlNotFoundException.class, () -> urlService.deleteById(99999L));
    }

    @Test
    void deleteByShortCode_ShouldDeleteUrl_WhenExists() {
        URL url = new URL();
        url.setOriginalUrl(TEST_ORIGINAL_URL);
        url.setShortCode(TEST_SHORT_CODE);
        urlRepository.save(url);

        urlService.deleteByShortCode(TEST_SHORT_CODE);
        assertTrue(urlRepository.findByShortCode(TEST_SHORT_CODE).isEmpty());
    }

    @Test
    void deleteByShortCode_ShouldThrowUrlNotFoundException_WhenNotExists() {
        assertThrows(UrlNotFoundException.class, () -> urlService.deleteByShortCode("nonExistentCode"));
    }

    @Test
    void deleteByOriginalURL_ShouldDeleteUrl_WhenExists() {
        URL url = new URL();
        url.setOriginalUrl(TEST_ORIGINAL_URL);
        url.setShortCode(TEST_SHORT_CODE);
        urlRepository.save(url);

        urlService.deleteByOriginalURL(TEST_ORIGINAL_URL);
        assertTrue(urlRepository.findByOriginalUrl(TEST_ORIGINAL_URL).isEmpty());
    }

    @Test
    void deleteByOriginalURL_ShouldThrowUrlNotFoundException_WhenNotExists() {
        assertThrows(UrlNotFoundException.class, () -> urlService.deleteByOriginalURL("https://non-existent.com"));
    }

    // ==========================================
    // List All Tests
    // ==========================================
    @Test
    void findAll_ShouldReturnAllUrls() {
        URL url = new URL();
        url.setOriginalUrl(TEST_ORIGINAL_URL);
        url.setShortCode(TEST_SHORT_CODE);
        urlRepository.save(url);

        try {
            List<URL> all = urlService.findAll();
            assertFalse(all.isEmpty());
            assertTrue(all.stream().anyMatch(u -> u.getShortCode().equals(TEST_SHORT_CODE)));
        } finally {
            urlRepository.delete(url);
        }
    }
}
