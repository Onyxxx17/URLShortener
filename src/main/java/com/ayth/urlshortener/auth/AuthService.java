package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;
import com.ayth.urlshortener.email.EmailVerificationToken;
import com.ayth.urlshortener.email.EmailVerificationTokenRepository;
import com.ayth.urlshortener.exception.EmailNotVerifiedException;
import com.ayth.urlshortener.exception.InvalidCredentialsException;
import com.ayth.urlshortener.exception.UserAlreadyExistsException;
import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    // ── Register ─────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        sendVerificationEmail(user);

        return AuthResponse.builder()
                .message("Registration successful. Please check your email to verify your account.")
                .user(toUserDto(user))
                .build();
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(
                    "Please verify your email address before logging in.");
        }

        String accessToken = jwtService.generateToken(principal);

        return AuthResponse.builder()
                .message("Login successful")
                .accessToken(accessToken)
                .user(toUserDto(user))
                .build();
    }

    // ── Email verification ────────────────────────────────────────────────────

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(rawToken);
        } catch (IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Invalid verification token format.");
        }

        EmailVerificationToken verificationToken = tokenRepository
                .findByToken(tokenUuid)
                .orElseThrow(() -> new InvalidCredentialsException("Verification token not found."));

        if (verificationToken.isUsed()) {
            throw new InvalidCredentialsException("This verification token has already been used.");
        }
        if (verificationToken.isExpired()) {
            throw new InvalidCredentialsException("This verification token has expired.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsedAt(Instant.now());
        tokenRepository.save(verificationToken);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Creates a 24-hour verification token and "sends" it.
     * Currently logs to console — wire up Spring Mail here later.
     */
    private void sendVerificationEmail(User user) {
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(UUID.randomUUID());
        verificationToken.setUser(user);
        verificationToken.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.save(verificationToken);

        // TODO: replace with actual email sending (e.g. Spring Mail / SendGrid)
        System.out.printf(
                "%n[EMAIL VERIFICATION] To: %s%n" +
                "Verify your account: GET /verify-email?token=%s%n%n",
                user.getEmail(),
                verificationToken.getToken()
        );
    }

    private AuthResponse.UserDto toUserDto(User user) {
        return AuthResponse.UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}