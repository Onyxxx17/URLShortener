package com.ayth.urlshortener.url;

import com.ayth.urlshortener.auth.UserPrincipal;
import com.ayth.urlshortener.dto.request.CreateUrlRequest;
import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import com.ayth.urlshortener.users.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import org.springframework.web.bind.annotation.*;

import com.ayth.urlshortener.qr.QRCodeService;
import com.google.zxing.WriterException;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/")
class URLController {
    private final URLService urlService;
    private final QRCodeService qrCodeService;

    @Autowired
    public URLController(URLService urlService, QRCodeService qrCodeService) {
        this.urlService = urlService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]{7,}}")
    public void redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = "Referer", required = false) String referer,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletResponse response
    ) throws IOException {
        String originalUrl = urlService.getUrlForRedirect(shortCode, referer, userAgent);
        response.sendRedirect(originalUrl);
    }

    @GetMapping(value = "/{shortCode:[a-zA-Z0-9]{7,}}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQRCode(
            @PathVariable String shortCode,
            HttpServletRequest request
    ) throws WriterException, IOException {
        // Validate that the short code exists
        urlService.getOriginalUrl(shortCode);

        String baseUrl = request.getScheme() + "://" +
                        request.getServerName() +
                        (request.getServerPort() != 80 && request.getServerPort() != 443
                            ? ":" + request.getServerPort()
                            : "");
        String fullUrl = baseUrl + "/" + shortCode;

        byte[] qrImage = qrCodeService.generateQRCodeImage(fullUrl, 250, 250);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(qrImage);
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

        // Retrieve the authenticated user directly from the SecurityContext
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
    }

    @GetMapping("/urls/my-urls")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<CreateUrlResponse>> getMyUrls(
            HttpServletRequest request,
            Authentication authentication) {
        String baseUrl = request.getScheme() + "://" +
                        request.getServerName() +
                        (request.getServerPort() != 80 && request.getServerPort() != 443
                            ? ":" + request.getServerPort()
                            : "");
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        return ResponseEntity.ok(urlService.getUrlsByUser(user, baseUrl));
    }

    @DeleteMapping("/urls/{shortCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable String shortCode,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        urlService.deleteByShortCodeForUser(shortCode, user);
        return ResponseEntity.noContent().build();
    }
}
