package com.ayth.urlshortener.url;

import com.ayth.urlshortener.auth.JwtAuthenticationFilter;
import com.ayth.urlshortener.auth.JwtService;
import com.ayth.urlshortener.auth.UserDetailsServiceImpl;
import com.ayth.urlshortener.auth.UserPrincipal;
import com.ayth.urlshortener.config.RateLimitingService;
import com.ayth.urlshortener.config.SecurityConfig;
import com.ayth.urlshortener.dto.request.CreateUrlRequest;
import com.ayth.urlshortener.dto.response.CreateUrlResponse;
import com.ayth.urlshortener.dto.response.StatsResponse;
import com.ayth.urlshortener.exception.GlobalExceptionHandler;
import com.ayth.urlshortener.exception.UrlExpiredException;
import com.ayth.urlshortener.exception.UrlNotFoundException;
import com.ayth.urlshortener.qr.QRCodeService;
import com.ayth.urlshortener.users.User;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer slice tests for URLController.
 * Uses @WebMvcTest — only the web layer, not the full context.
 * URLService and QRCodeService are mocked.
 */
@WebMvcTest(controllers = URLController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, AuthenticationConfiguration.class})
@EnableWebSecurity
class URLControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean URLService urlService;
    @MockitoBean QRCodeService qrCodeService;

    // JwtAuthenticationFilter is deliberately NOT mocked: a mocked Filter never
    // calls chain.doFilter, so it swallows every request and MockMvc returns an
    // empty 200. The real filter is constructed from these two mocks and simply
    // passes through when no token is present.
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsServiceImpl userDetailsService;

    // WebConfig is a WebMvcConfigurer, so @WebMvcTest loads it along with the
    // rate-limit interceptors it wires up. Those need a RateLimitingService,
    // which is not part of the web slice — hand them a bucket that never throttles
    // so these tests exercise the controller rather than the limiter.
    @MockitoBean RateLimitingService rateLimitingService;

    private User testUser;
    private UserPrincipal testPrincipal;
    private CreateUrlResponse sampleResponse;

    @BeforeEach
    void allowAllRateLimits() {
        lenient().when(rateLimitingService.resolveUserBucket(anyString()))
                .thenAnswer(invocation -> unthrottledBucket());
        lenient().when(rateLimitingService.resolveIpBucket(anyString()))
                .thenAnswer(invocation -> unthrottledBucket());
        lenient().when(rateLimitingService.resolveQrIpBucket(anyString()))
                .thenAnswer(invocation -> unthrottledBucket());
        lenient().when(rateLimitingService.resolveRedirectIpBucket(anyString()))
                .thenAnswer(invocation -> unthrottledBucket());
    }

    // Bucket4j caps refill at 1 token/nanosecond, so Long.MAX_VALUE is rejected.
    // A million per minute is effectively unlimited for a test.
    private static final long UNTHROTTLED_CAPACITY = 1_000_000L;

    private static Bucket unthrottledBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(
                        UNTHROTTLED_CAPACITY,
                        Refill.intervally(UNTHROTTLED_CAPACITY, Duration.ofMinutes(1))))
                .build();
    }

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");
        testPrincipal = new UserPrincipal(testUser);

        sampleResponse = CreateUrlResponse.builder()
                .id(1L)
                .shortCode("abc1234")
                .shortUrl("http://localhost/abc1234")
                .originalUrl("https://example.com")
                .createdAt(Instant.now())
                .clickCount(0L)
                .createdBy("test@example.com")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /{shortCode} — redirect
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void redirect_validShortCode_returns302() throws Exception {
        when(urlService.getUrlForRedirect(eq("abc1234"), any(), any()))
                .thenReturn("https://example.com");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void redirect_notFound_returns404() throws Exception {
        when(urlService.getUrlForRedirect(eq("missing1"), any(), any()))
                .thenThrow(new UrlNotFoundException("not found"));

        mockMvc.perform(get("/missing1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirect_expiredUrl_returns410() throws Exception {
        when(urlService.getUrlForRedirect(eq("expired1"), any(), any()))
                .thenThrow(new UrlExpiredException("expired"));

        mockMvc.perform(get("/expired1"))
                .andExpect(status().isGone());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /create — create short URL
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createUrl_authenticatedValidRequest_returns201() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com");

        when(urlService.createUrlWithResponse(anyString(), anyString(), any(User.class), any()))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/create")
                        .with(user(testPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void createUrl_unauthenticated_returns401() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("https://example.com");

        mockMvc.perform(post("/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUrl_invalidUrl_returns400() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("not-a-valid-url");

        mockMvc.perform(post("/create")
                        .with(user(testPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUrl_blankUrl_returns400() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("");

        mockMvc.perform(post("/create")
                        .with(user(testPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUrl_selfDomain_returns400() throws Exception {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl("http://localhost/abc1234");

        when(urlService.createUrlWithResponse(anyString(), anyString(), any(User.class), any()))
                .thenThrow(new IllegalArgumentException("Cannot shorten URLs pointing to our own domain."));

        mockMvc.perform(post("/create")
                        .with(user(testPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot shorten URLs pointing to our own domain."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /urls/{shortCode}/stats
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getStats_existingUrl_returns200WithStats() throws Exception {
        StatsResponse stats = StatsResponse.builder()
                .id(1L)
                .shortCode("abc1234")
                .originalUrl("https://example.com")
                .clickCount(10L)
                .createdAt(Instant.now())
                .isExpired(false)
                .ageInDays(3L)
                .recentClicks(List.of())
                .build();

        when(urlService.createUrlStatsResponse(eq("abc1234"), any(User.class))).thenReturn(stats);

        mockMvc.perform(get("/urls/abc1234/stats")
                        .with(user(testPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.clickCount").value(10))
                .andExpect(jsonPath("$.isExpired").value(false));
    }

    @Test
    void getStats_notFound_returns404WithMessage() throws Exception {
        when(urlService.createUrlStatsResponse(eq("nothere"), any(User.class)))
                .thenThrow(new UrlNotFoundException("Url with short code nothere not found"));

        mockMvc.perform(get("/urls/nothere/stats")
                        .with(user(testPrincipal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Url with short code nothere not found"));
    }

    @Test
    void getStats_unauthenticated_returns401WithEmptyBody() throws Exception {
        mockMvc.perform(get("/urls/abc1234/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void getStats_nonOwner_returns403WithMessage() throws Exception {
        when(urlService.createUrlStatsResponse(eq("abc1234"), any(User.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException(
                        "You do not own this URL"));

        mockMvc.perform(get("/urls/abc1234/stats")
                        .with(user(testPrincipal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /urls/my-urls
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getMyUrls_authenticated_returns200() throws Exception {
        when(urlService.getUrlsByUser(any(User.class), anyString()))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/urls/my-urls")
                        .with(user(testPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shortCode").value("abc1234"));
    }

    @Test
    void getMyUrls_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/urls/my-urls"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /urls/{shortCode}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deleteUrl_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/urls/abc1234")
                        .with(user(testPrincipal)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUrl_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/urls/abc1234"))
                .andExpect(status().isUnauthorized());
    }
}
