package com.ayth.urlshortener.url;

import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import com.ayth.urlshortener.exception.UrlAlreadyExistsException;
import com.ayth.urlshortener.exception.UrlExpiredException;
import com.ayth.urlshortener.exception.UrlNotFoundException;
import com.ayth.urlshortener.util.ShortCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
class URLService {

    private final URLRepository urlRepository;
    private final URLClickTracker urlClickTracker;

    @Autowired
    public URLService(URLRepository urlRepository, URLClickTracker urlClickTracker) {
        this.urlRepository = urlRepository;
        this.urlClickTracker = urlClickTracker;
    }

    public URL findByShortURL(String shortURL) {
        //Find with redis first (Implement later)
        Optional<URL> optional = urlRepository.findByShortCode(shortURL);
        return optional.orElseThrow(() -> new UrlNotFoundException("Url with short code " + shortURL + " not found"));
    }


    public String getUrlForRedirect(String shortCode) {
        URL url = findByShortURL(shortCode);

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(Instant.now())) {
            throw new UrlExpiredException(
                "This short URL has expired and is no longer available"
            );
        }

        urlClickTracker.incrementClickCountAndUpdateLastAccessed(shortCode);
        return url.getOriginalUrl();
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
                .build();
    }

    public CreateUrlResponse createUrlWithResponse(String originalUrl, String baseUrl) {
        URL newURL = this.createUrl(originalUrl);
        String fullShortUrl = baseUrl + "/" + newURL.getShortCode();

        return CreateUrlResponse.builder()
                .id(newURL.getId())
                .shortUrl(fullShortUrl)
                .shortCode(newURL.getShortCode())
                .originalUrl(newURL.getOriginalUrl())
                .createdAt(newURL.getCreatedAt())
                .expiresAt(newURL.getExpiresAt())
                .clickCount(newURL.getClickCount())
                .build();
    }

    public URL createUrl(String originalUrl) {
        String shortCode;
        Optional<URL> optional = urlRepository.findByOriginalUrl(originalUrl);
        if(optional.isPresent()) {
            throw new UrlAlreadyExistsException("URL already exists");
        }

        URL newURL = new URL();
        newURL.setOriginalUrl(originalUrl);

        do{
            shortCode = ShortCodeGenerator.generateShortCode();
        }while (urlRepository.findByShortCode(newURL.getShortCode()).isPresent());

        //Add to redis (Implement Later)

        newURL.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        newURL.setClickCount(0);
        newURL.setShortCode(shortCode);

        urlRepository.save(newURL);

        return newURL;
    }

    public void deleteById(Long id) {
        Optional<URL> optional = urlRepository.findById(id);
        if(optional.isEmpty()){
            throw new UrlNotFoundException("URL does not exist");
        }
        urlRepository.deleteById(id);
    }

    public void deleteByShortCode(String shortCode) {
        Optional<URL> optional = urlRepository.findByShortCode(shortCode);
        if(optional.isPresent()) {
            urlRepository.deleteByShortCode(shortCode);
        } else{
            throw new UrlNotFoundException("URL does not exist");
        }
    }

    public void deleteByOriginalURL(String originalURL) {
        Optional<URL> optional = urlRepository.findByOriginalUrl(originalURL);
        if(optional.isEmpty()){
            throw new UrlNotFoundException("URL does not exist");
        }
        urlRepository.deleteByOriginalUrl(originalURL);
    }

    public List<URL> findAll() {
        return urlRepository.findAll();
    }


}
