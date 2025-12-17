package com.ayth.urlshortener.url;

import com.ayth.urlshortener.dto.request.CreateUrlRequest;
import com.ayth.urlshortener.exception.UrlNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.ayth.urlshortener.url.URL;
import com.ayth.urlshortener.url.URLService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/")
class URLController {
    private final URLService urlService;

    @Autowired
    public URLController(URLService urlService) {
        this.urlService = urlService;
    }

    /// ========Find By Short Code and Redirect========
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> getURL(@PathVariable String shortCode) {
       URL url = urlService.findByShortURL(shortCode);

       if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(Instant.now())) {
           throw new UrlNotFoundException("URL has expired");
       }

       //Get original URL using short code
       String originalUrl = url.getOriginalUrl();

       //Resave the url with new expiry time (Implement later)
       urlService.save(url);

       //Redirect
       return ResponseEntity.status(302)
               .header("Location", originalUrl).build();
    }

    /// =======Create a new URL mapping=======
    @PostMapping("/create")
    public ResponseEntity<Map<String,String>> createURL(@RequestBody CreateUrlRequest createUrlRequest) {
        String originalURL = createUrlRequest.getOriginalUrl();

        URL url = urlService.addUrl(originalURL);
        String fullShortUrl = "http://localhost:8080/" + url.getShortCode();
        Map<String, String> response = new HashMap<>();
        response.put("shortUrl", fullShortUrl);
        response.put("shortCode", url.getShortCode());
        response.put("originalUrl", originalURL);
        response.put("Created at", url.getCreatedAt().toString());

        return ResponseEntity.status(HttpStatus.CREATED.value()).body(response);
    }

}
