package com.ayth.urlshortener.url;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import com.ayth.urlshortener.exception.UrlAlreadyExistsException;
import com.ayth.urlshortener.exception.UrlExpiredException;
import com.ayth.urlshortener.exception.UrlNotFoundException;
import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;

/**
 * Pure unit tests for URLService.
 * No Spring context — all collaborators are Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class URLServiceTest {

    @Mock URLRepository urlRepository;
    @Mock URLClickTracker urlClickTracker;
    @Mock UserRepository userRepository;
    @Mock URLClickEventRepository urlClickEventRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks URLService urlService;

    private User user;
    private URL activeUrl;
    private URL expiredUrl;

    private static final String SHORT_CODE = "abc1234";
    private static final String ORIGINAL_URL = "https://example.com/some/path";
    private static final String BASE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setUsername("testuser");

        activeUrl = new URL();
        activeUrl.setId(1L);
        activeUrl.setShortCode(SHORT_CODE);
        activeUrl.setOriginalUrl(ORIGINAL_URL);
        activeUrl.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        activeUrl.setClickCount(5);
        activeUrl.setUser(user);

        expiredUrl = new URL();
        expiredUrl.setId(2L);
        expiredUrl.setShortCode("exp1234");
        expiredUrl.setOriginalUrl(ORIGINAL_URL);
        expiredUrl.setCreatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        expiredUrl.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        expiredUrl.setClickCount(0);
        expiredUrl.setUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOriginalUrl — cache HIT paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getOriginalUrl_cacheHit_returnsOriginalUrl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:redirect:" + SHORT_CODE))
                .thenReturn(ORIGINAL_URL + "|null");

        String result = urlService.getOriginalUrl(SHORT_CODE);

        assertThat(result).isEqualTo(ORIGINAL_URL);
        verifyNoInteractions(urlRepository);
    }

    @Test
    void getOriginalUrl_cacheHitNegative_throwsUrlNotFoundException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:redirect:" + SHORT_CODE)).thenReturn("NOT_FOUND");

        assertThatThrownBy(() -> urlService.getOriginalUrl(SHORT_CODE))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void getOriginalUrl_cacheHitExpired_throwsUrlExpiredException() {
        String expiry = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:redirect:" + SHORT_CODE))
                .thenReturn(ORIGINAL_URL + "|" + expiry);
        when(redisTemplate.delete("url:redirect:" + SHORT_CODE)).thenReturn(true);

        assertThatThrownBy(() -> urlService.getOriginalUrl(SHORT_CODE))
                .isInstanceOf(UrlExpiredException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOriginalUrl — cache MISS paths (falls back to DB)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getOriginalUrl_cacheMiss_hitsDbAndCaches() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(activeUrl));

        String result = urlService.getOriginalUrl(SHORT_CODE);

        assertThat(result).isEqualTo(ORIGINAL_URL);
        verify(valueOps).set(eq("url:redirect:" + SHORT_CODE), anyString(), any());
    }

    @Test
    void getOriginalUrl_cacheMiss_notFound_negativelyCachesAndThrows() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.getOriginalUrl(SHORT_CODE))
                .isInstanceOf(UrlNotFoundException.class);

        verify(valueOps).set(eq("url:redirect:" + SHORT_CODE), eq("NOT_FOUND"), any());
    }

    @Test
    void getOriginalUrl_cacheMiss_expiredUrl_throwsUrlExpiredException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        when(urlRepository.findByShortCode("exp1234")).thenReturn(Optional.of(expiredUrl));

        assertThatThrownBy(() -> urlService.getOriginalUrl("exp1234"))
                .isInstanceOf(UrlExpiredException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createUrlWithResponse
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createUrlWithResponse_success_returnsResponse() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(urlRepository.findByUserAndOriginalUrl(user, ORIGINAL_URL)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(urlRepository.save(any(URL.class))).thenAnswer(inv -> {
            URL url = inv.getArgument(0);
            if (url.getId() == null) url.setId(99L);
            return url;
        });

        CreateUrlResponse response = urlService.createUrlWithResponse(ORIGINAL_URL, BASE_URL, user, null);

        assertThat(response).isNotNull();
        assertThat(response.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
        assertThat(response.getShortUrl()).startsWith(BASE_URL + "/");
        assertThat(response.getCreatedBy()).isEqualTo(user.getEmail());
    }

    @Test
    void createUrlWithResponse_selfDomain_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                urlService.createUrlWithResponse("http://localhost:8080/abc1234", BASE_URL, user, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot shorten URLs pointing to our own domain");
    }

    @Test
    void createUrlWithResponse_duplicateInRedis_throwsUrlAlreadyExistsException() {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        assertThatThrownBy(() ->
                urlService.createUrlWithResponse(ORIGINAL_URL, BASE_URL, user, null))
                .isInstanceOf(UrlAlreadyExistsException.class);
    }

    @Test
    void createUrlWithResponse_duplicateInDb_throwsUrlAlreadyExistsException() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(urlRepository.findByUserAndOriginalUrl(user, ORIGINAL_URL)).thenReturn(Optional.of(activeUrl));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertThatThrownBy(() ->
                urlService.createUrlWithResponse(ORIGINAL_URL, BASE_URL, user, null))
                .isInstanceOf(UrlAlreadyExistsException.class);
    }

    @Test
    void createUrlWithResponse_withExpiry_setsExpiresAt() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(urlRepository.findByUserAndOriginalUrl(user, ORIGINAL_URL)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(urlRepository.save(any(URL.class))).thenAnswer(inv -> {
            URL url = inv.getArgument(0);
            if (url.getId() == null) url.setId(99L);
            return url;
        });

        CreateUrlResponse response = urlService.createUrlWithResponse(ORIGINAL_URL, BASE_URL, user, 7);

        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createUrlStatsResponse
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createUrlStatsResponse_activeUrl_returnsCorrectStats() {
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(activeUrl));
        when(urlClickEventRepository.findByUrlOrderByClickTimestampDesc(activeUrl))
                .thenReturn(List.of());

        StatsResponse stats = urlService.createUrlStatsResponse(SHORT_CODE, user);

        assertThat(stats.getShortCode()).isEqualTo(SHORT_CODE);
        assertThat(stats.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
        assertThat(stats.getClickCount()).isEqualTo(5);
        assertThat(stats.getIsExpired()).isFalse();
        assertThat(stats.getAgeInDays()).isGreaterThanOrEqualTo(2L);
        assertThat(stats.getDaysUntilExpiry()).isNull(); // no expiry set
    }

    @Test
    void createUrlStatsResponse_expiredUrl_isExpiredTrue() {
        when(urlRepository.findByShortCode("exp1234")).thenReturn(Optional.of(expiredUrl));
        when(urlClickEventRepository.findByUrlOrderByClickTimestampDesc(expiredUrl))
                .thenReturn(List.of());

        StatsResponse stats = urlService.createUrlStatsResponse("exp1234", user);

        assertThat(stats.getIsExpired()).isTrue();
        assertThat(stats.getDaysUntilExpiry()).isNull();
    }

    @Test
    void createUrlStatsResponse_notFound_throwsUrlNotFoundException() {
        when(urlRepository.findByShortCode("notExist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.createUrlStatsResponse("notExist", user))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void createUrlStatsResponse_nonOwner_throwsAccessDeniedException() {

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(activeUrl));

        assertThatThrownBy(() -> urlService.createUrlStatsResponse(SHORT_CODE, otherUser))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(urlClickEventRepository, never()).findByUrlOrderByClickTimestampDesc(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteByShortCodeForUser
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deleteByShortCodeForUser_owner_deletesSuccessfully() {
        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(activeUrl));
        when(redisTemplate.delete("url:redirect:" + SHORT_CODE)).thenReturn(true);

        urlService.deleteByShortCodeForUser(SHORT_CODE, user);

        verify(urlRepository).delete(activeUrl);
    }

    @Test
    void deleteByShortCodeForUser_nonOwner_throwsAccessDeniedException() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        when(urlRepository.findByShortCode(SHORT_CODE)).thenReturn(Optional.of(activeUrl));

        assertThatThrownBy(() -> urlService.deleteByShortCodeForUser(SHORT_CODE, otherUser))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(urlRepository, never()).delete(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUrlsByUser
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getUrlsByUser_returnsListOfResponses() {
        when(urlRepository.findByUserOrderByCreatedAtDesc(user))
                .thenReturn(List.of(activeUrl));

        List<CreateUrlResponse> result = urlService.getUrlsByUser(user, BASE_URL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getShortCode()).isEqualTo(SHORT_CODE);
        assertThat(result.get(0).getShortUrl()).startsWith(BASE_URL + "/");
    }
}
