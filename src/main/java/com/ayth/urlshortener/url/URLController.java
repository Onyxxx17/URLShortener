package com.ayth.urlshortener.url;

import com.ayth.urlshortener.dto.request.CreateUrlRequest;
import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        String originalUrl = urlService.getUrlForRedirect(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
               .header("Location", originalUrl)
               .build();
    }

    /// =======Create a new URL mapping=======
    @PostMapping("/create")
    public ResponseEntity<CreateUrlResponse> createURL(
            @Valid @RequestBody CreateUrlRequest createUrlRequest,
            HttpServletRequest request,
            HttpSession session) {

        // Build base URL from request
        String baseUrl = request.getScheme() + "://" + 
                        request.getServerName() + 
                        (request.getServerPort() != 80 && request.getServerPort() != 443 
                            ? ":" + request.getServerPort() 
                            : "");

        CreateUrlResponse response = urlService.createUrlWithResponse(
            createUrlRequest.getOriginalUrl(), 
            baseUrl, session
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /// ========Get URL Statistics========
    @GetMapping("/urls/{shortCode}/stats")
    public ResponseEntity<StatsResponse> getStats(@PathVariable String shortCode) {
        StatsResponse response = urlService.createUrlStatsResponse(shortCode);
        return ResponseEntity.ok(response);
    }

}
