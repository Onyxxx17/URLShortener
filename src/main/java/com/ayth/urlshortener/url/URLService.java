package com.ayth.urlshortener.url;

import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import com.ayth.urlshortener.exception.UrlAlreadyExistsException;
import com.ayth.urlshortener.exception.UrlExpiredException;
import com.ayth.urlshortener.exception.UrlNotFoundException;
import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sqids.Sqids;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
class URLService {

    private static final Logger log = LoggerFactory.getLogger(URLService.class);
    private static final Sqids SQIDS = Sqids.builder().minLength(7).build();
    private static final String CACHE_PREFIX = "url:redirect:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final URLRepository urlRepository;
    private final URLClickTracker urlClickTracker;
    private final URLClickEventRepository urlClickEventRepository;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    URLService(URLRepository urlRepository, URLClickTracker urlClickTracker,
               UserRepository userRepository, URLClickEventRepository urlClickEventRepository,
               StringRedisTemplate redisTemplate) {
        this.urlRepository = urlRepository;
        this.urlClickTracker = urlClickTracker;
        this.userRepository = userRepository;
        this.urlClickEventRepository = urlClickEventRepository;
        this.redisTemplate = redisTemplate;
    }

    public String getUrlForRedirect(String shortCode, String referer, String userAgent) {
        // 1. Try Redis cache first
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);

        if (cached != null) {
            log.debug("[CACHE] HIT for shortCode: {}", shortCode);
            String[] parts = cached.split("\\|", 2);
            String originalUrl = parts[0];
            String expiresAtStr = parts.length > 1 ? parts[1] : null;

            // Check expiry from cached data — no DB hit needed
            if (expiresAtStr != null && !expiresAtStr.equals("null")) {
                Instant expiresAt = Instant.parse(expiresAtStr);
                if (expiresAt.isBefore(Instant.now())) {
                    evictCache(shortCode);
                    throw new UrlExpiredException("This short URL has expired and is no longer available");
                }
            }

            urlClickTracker.incrementClickCountAndUpdateLastAccessed(shortCode, referer, userAgent);
            return originalUrl;
        }

        // 2. Cache miss — fall back to Postgres
        log.debug("[CACHE] MISS for shortCode: {}", shortCode);
        URL url = findByShortURL(shortCode);

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlExpiredException("This short URL has expired and is no longer available");
        }

        // 3. Populate cache with fixed 24h TTL
        cacheUrl(shortCode, url.getOriginalUrl(), url.getExpiresAt());

        urlClickTracker.incrementClickCountAndUpdateLastAccessed(shortCode, referer, userAgent);
        return url.getOriginalUrl();
    }

    public URL findByShortURL(String shortURL) {
        Optional<URL> optional = urlRepository.findByShortCode(shortURL);
        return optional.orElseThrow(() -> new UrlNotFoundException("Url with short code " + shortURL + " not found"));
    }

    public StatsResponse createUrlStatsResponse(String shortCode) {
        URL url = this.findByShortURL(shortCode);

        url.setLastAccessedAt(Instant.now());
        urlRepository.save(url);

        Instant now = Instant.now();
        Long daysUntilExpiry = null;
        boolean isExpired = false;

        if (url.getExpiresAt() != null) {
            isExpired = url.getExpiresAt().isBefore(now);
            if (!isExpired) {
                Duration duration = Duration.between(now, url.getExpiresAt());
                daysUntilExpiry = duration.toDays();
            }
        }

        Duration age = Duration.between(url.getCreatedAt(), now);
        Long ageInDays = age.toDays();

        List<URLClickEvent> clickEvents = urlClickEventRepository.findByUrlOrderByClickTimestampDesc(url);
        List<StatsResponse.ClickEventDto> recentClicks = clickEvents.stream()
                .map(event -> StatsResponse.ClickEventDto.builder()
                        .clickedAt(event.getClickTimestamp())
                        .referer(event.getReferer())
                        .userAgent(event.getUserAgent())
                        .build())
                .toList();

        return StatsResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .clickCount(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .lastAccessedAt(url.getLastAccessedAt())
                .daysUntilExpiry(daysUntilExpiry)
                .isExpired(isExpired)
                .ageInDays(ageInDays)
                .recentClicks(recentClicks)
                .build();
    }

    /**
     * @param user The authenticated {@link User} resolved from the
     *             {@link org.springframework.security.core.context.SecurityContext}
     *             — no extra DB lookup needed.
     */
    public CreateUrlResponse createUrlWithResponse(String originalUrl, String baseUrl, User user) {
        URL newURL = this.createUrl(originalUrl, user);
        String fullShortUrl = baseUrl + "/" + newURL.getShortCode();

        return CreateUrlResponse.builder()
                .id(newURL.getId())
                .shortUrl(fullShortUrl)
                .shortCode(newURL.getShortCode())
                .originalUrl(newURL.getOriginalUrl())
                .createdAt(newURL.getCreatedAt())
                .expiresAt(newURL.getExpiresAt())
                .clickCount(newURL.getClickCount())
                .createdBy(newURL.getUser().getEmail())
                .build();
    }

    @Transactional
    public URL createUrl(String originalUrl, User user) {
        Optional<URL> optional = urlRepository.findByUserAndOriginalUrl(user, originalUrl);
        if (optional.isPresent()) {
            throw new UrlAlreadyExistsException("URL already exists");
        }

        URL newURL = new URL();
        newURL.setOriginalUrl(originalUrl);
        newURL.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        newURL.setClickCount(0);
        newURL.setUser(user);

        newURL = urlRepository.save(newURL);

        String shortCode = SQIDS.encode(List.of(newURL.getId()));
        newURL.setShortCode(shortCode);

        newURL = urlRepository.save(newURL);

        // Pre-warm cache so first redirect is instant
        cacheUrl(newURL.getShortCode(), newURL.getOriginalUrl(), newURL.getExpiresAt());
        log.debug("[CACHE] Pre-warmed cache for shortCode: {}", newURL.getShortCode());

        return newURL;
    }

    public void deleteById(Long id) {
        URL url = urlRepository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("URL does not exist"));
        evictCache(url.getShortCode());
        urlRepository.deleteById(id);
    }

    @Transactional
    public void deleteByShortCode(String shortCode) {
        if (!urlRepository.findByShortCode(shortCode).isPresent()) {
            throw new UrlNotFoundException("URL does not exist");
        }
        evictCache(shortCode);
        urlRepository.deleteByShortCode(shortCode);
    }

    @Transactional
    public void deleteByOriginalURL(String originalURL) {
        URL url = urlRepository.findByOriginalUrl(originalURL)
                .orElseThrow(() -> new UrlNotFoundException("URL does not exist"));
        evictCache(url.getShortCode());
        urlRepository.deleteByOriginalUrl(originalURL);
    }

    public List<URL> findAll() {
        return urlRepository.findAll();
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private void cacheUrl(String shortCode, String originalUrl, Instant expiresAt) {
        String value = originalUrl + "|" + expiresAt;
        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, value, DEFAULT_TTL);
    }

    private void evictCache(String shortCode) {
        Boolean deleted = redisTemplate.delete(CACHE_PREFIX + shortCode);
        log.debug("[CACHE] Evicted shortCode: {} (existed: {})", shortCode, deleted);
    }
}
