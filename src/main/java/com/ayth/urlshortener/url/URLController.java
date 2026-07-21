package com.ayth.urlshortener.url;

import com.ayth.urlshortener.auth.UserPrincipal;
import com.ayth.urlshortener.dto.request.CreateUrlRequest;
import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import com.ayth.urlshortener.users.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
class URLController {
    private final URLService urlService;

    @Autowired
    public URLController(URLService urlService) {
        this.urlService = urlService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> getURL(
            @PathVariable String shortCode,
            @RequestHeader(value = "referer", required = false) String referer,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        String originalUrl = urlService.getUrlForRedirect(shortCode, referer, userAgent);

        return ResponseEntity.status(HttpStatus.FOUND)
               .header("Location", originalUrl)
               .build();
    }

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreateUrlResponse> createURL(
            @Valid @RequestBody CreateUrlRequest createUrlRequest,
            HttpServletRequest request,
            Authentication authentication) {

        // Build base URL
        String baseUrl = request.getScheme() + "://" +
                        request.getServerName() +
                        (request.getServerPort() != 80 && request.getServerPort() != 443
                            ? ":" + request.getServerPort()
                            : "");

        // Retrieve the authenticated user directly from the SecurityContext —
        // no additional DB call needed since UserPrincipal holds the entity.
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        CreateUrlResponse response = urlService.createUrlWithResponse(
            createUrlRequest.getOriginalUrl(),
            baseUrl,
            user,
            createUrlRequest.getExpiresInDays()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/urls/{shortCode}/stats")
    public ResponseEntity<StatsResponse> getStats(@PathVariable String shortCode) {
        StatsResponse response = urlService.createUrlStatsResponse(shortCode);
        return ResponseEntity.ok(response);
    }}
