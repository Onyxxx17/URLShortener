package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.config.RateLimitingService;
import com.ayth.urlshortener.config.SecurityConfig;
import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;
import com.ayth.urlshortener.exception.GlobalExceptionHandler;
import com.ayth.urlshortener.exception.InvalidCredentialsException;
import com.ayth.urlshortener.exception.UserAlreadyExistsException;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer slice tests for AuthController.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, AuthenticationConfiguration.class})
@EnableWebSecurity
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthService authService;

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

    @BeforeEach
    void allowAllRateLimits() {
        // All four resolvers are stubbed, not just the IP one: "/register" is 8
        // alphanumeric characters, so it also matches the redirect interceptor's
        // /{shortCode:[a-zA-Z0-9]{7,}} pattern and consumes a redirect bucket too.
        lenient().when(rateLimitingService.resolveIpBucket(anyString()))
                .thenAnswer(invocation -> unthrottledBucket());
        lenient().when(rateLimitingService.resolveUserBucket(anyString()))
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

    // ─────────────────────────────────────────────────────────────────────────
    // POST /register
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void register_validRequest_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("password123");

        AuthResponse response = AuthResponse.builder()
                .message("Registration successful. Please check your email to verify your account.")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registration successful. Please check your email to verify your account."));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("existing@example.com");
        req.setPassword("password123");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new UserAlreadyExistsException("Email"));

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("short"); // fails @Size(min=8)

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("not-an-email");
        req.setPassword("password123");

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /login
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200AndSetsCookie() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .username("testuser")
                .emailVerified(true)
                .build();

        AuthResponse response = AuthResponse.builder()
                .message("Login successful")
                .accessToken("mock.jwt.token")
                .user(userDto)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().httpOnly("jwt", true));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("");
        req.setPassword("password123");

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /verify-email
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void verifyEmail_validToken_returns200() throws Exception {
        String token = UUID.randomUUID().toString();

        mockMvc.perform(get("/verify-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));
    }
}
