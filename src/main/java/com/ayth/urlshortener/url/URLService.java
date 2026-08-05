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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.net.URI;
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
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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

    public String getOriginalUrl(String shortCode) {
        // Redis cache first
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);

        if (cached != null) {
            log.debug("[CACHE] HIT for shortCode: {}", shortCode);
            
            // Negative Caching check
            if ("NOT_FOUND".equals(cached)) {
                throw new UrlNotFoundException("Url with short code " + shortCode + " not found");
            }

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

            return originalUrl;
        }

        // Cache miss — fall back to Postgres
        log.debug("[CACHE] MISS for shortCode: {}", shortCode);
        URL url;
        try {
            url = findByShortURL(shortCode);
        } catch (UrlNotFoundException e) {
            // Negative Caching
            redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, "NOT_FOUND", Duration.ofMinutes(5));
            throw e;
        }

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlExpiredException("This short URL has expired and is no longer available");
        }

        // Populate cache with fixed 24h TTL
        cacheUrl(shortCode, url.getOriginalUrl(), url.getExpiresAt());

        return url.getOriginalUrl();
    }

    public String getUrlForRedirect(String shortCode, String referer, String userAgent) {
        String originalUrl = getOriginalUrl(shortCode);
        urlClickTracker.incrementClickCountAndUpdateLastAccessed(shortCode, referer, userAgent);
        return originalUrl;
    }

    public URL findByShortURL(String shortURL) {
        Optional<URL> optional = urlRepository.findByShortCode(shortURL);
        return optional.orElseThrow(() -> new UrlNotFoundException("Url with short code " + shortURL + " not found"));
    }

    public StatsResponse createUrlStatsResponse(String shortCode) {
        URL url = findByShortURL(shortCode);
        Instant now = Instant.now();

        boolean isExpired = url.getExpiresAt() != null && url.getExpiresAt().isBefore(now);
        Long daysUntilExpiry = (url.getExpiresAt() != null && !isExpired) 
                ? ChronoUnit.DAYS.between(now, url.getExpiresAt()) 
                : null;
        Long ageInDays = ChronoUnit.DAYS.between(url.getCreatedAt(), now);

        List<URLClickEvent> events = urlClickEventRepository.findByUrlOrderByClickTimestampDesc(url);
        
        List<StatsResponse.ClickEventDto> recentClicks = events.stream()
                .map(event -> StatsResponse.ClickEventDto.builder()
                        .clickedAt(event.getClickTimestamp())
                        .referer(event.getReferer())
                        .userAgent(event.getUserAgent())
                        .build())
                .toList();

        StatsResponse response = StatsResponse.builder()
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
        return response;
    }

    public CreateUrlResponse createUrlWithResponse(String originalUrl, String baseUrl, User user, Integer expiresInDays) {
        try {
            URI originalUri = URI.create(originalUrl);
            URI baseUri = URI.create(baseUrl);
            if (baseUri.getHost() != null && baseUri.getHost().equalsIgnoreCase(originalUri.getHost())) {
                throw new IllegalArgumentException("Cannot shorten URLs pointing to our own domain.");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("Cannot shorten")) throw e;
        }

        URL newURL = this.createUrl(originalUrl, user, expiresInDays);
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
    public URL createUrl(String originalUrl, User user, Integer expiresInDays) {
        String duplicateCheckKey = "DUPLICATE_CHECK:" + user.getId() + ":" + originalUrl;
        
        // Check Redis if this user already created this URL recently
        if (Boolean.TRUE.equals(redisTemplate.hasKey(duplicateCheckKey))) {
            throw new UrlAlreadyExistsException("URL already exists");
        }

        // Check Postgres
        Optional<URL> optional = urlRepository.findByUserAndOriginalUrl(user, originalUrl);
        if (optional.isPresent()) {
            // Cache the existence for 15 minutes to prevent future DB hits
            redisTemplate.opsForValue().set(duplicateCheckKey, "1", Duration.ofMinutes(15));
            throw new UrlAlreadyExistsException("URL already exists");
        }

        URL newURL = new URL();
        newURL.setOriginalUrl(originalUrl);
        
        Instant expiryDate = expiresInDays == null ? null : Instant.now().plus(expiresInDays, ChronoUnit.DAYS);
        newURL.setExpiresAt(expiryDate);
        
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

    @Transactional
    public void deleteById(Long id) {
        URL url = urlRepository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("URL does not exist"));
        evictCache(url.getShortCode());
        urlRepository.delete(url);
    }

    @Transactional
    public void deleteByShortCode(String shortCode) {
        URL url = findByShortURL(shortCode);
        evictCache(shortCode);
        urlRepository.delete(url);
    }

    @Transactional
    public void deleteByOriginalURL(String originalURL) {
        URL url = urlRepository.findByOriginalUrl(originalURL)
                .orElseThrow(() -> new UrlNotFoundException("URL does not exist"));
        evictCache(url.getShortCode());
        urlRepository.delete(url);
    }

    public List<CreateUrlResponse> getUrlsByUser(User user, String baseUrl) {
        return urlRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(url -> CreateUrlResponse.builder()
                        .id(url.getId())
                        .shortUrl(baseUrl + "/" + url.getShortCode())
                        .shortCode(url.getShortCode())
                        .originalUrl(url.getOriginalUrl())
                        .createdAt(url.getCreatedAt())
                        .expiresAt(url.getExpiresAt())
                        .clickCount(url.getClickCount())
                        .createdBy(url.getUser().getEmail())
                        .build())
                .toList();
    }

    @Transactional
    public void deleteByShortCodeForUser(String shortCode, User user) {
        URL url = findByShortURL(shortCode);
        if (!url.getUser().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this URL");
        }
        evictCache(shortCode);
        urlRepository.delete(url);
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
