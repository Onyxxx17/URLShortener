package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;
import com.ayth.urlshortener.email.EmailService;
import com.ayth.urlshortener.email.EmailVerificationToken;
import com.ayth.urlshortener.email.EmailVerificationTokenRepository;
import com.ayth.urlshortener.exception.EmailNotVerifiedException;
import com.ayth.urlshortener.exception.InvalidCredentialsException;
import com.ayth.urlshortener.exception.UserAlreadyExistsException;
import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;


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

    public AuthResponse.UserDto getMe(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new AuthenticationCredentialsNotFoundException("Not authenticated");
        }
        return toUserDto(userPrincipal.getUser());
    }

    @Transactional
    public AuthResponse resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Return a generic message to avoid revealing whether the email exists
            return AuthResponse.builder()
                    .message("If that email is registered and unverified, a new link has been sent.")
                    .build();
        }

        if (user.isEmailVerified()) {
            return AuthResponse.builder()
                    .message("Your email is already verified. You can log in.")
                    .build();
        }

        // Delete ALL existing tokens for this user before issuing a new one
        tokenRepository.deleteAllByUser(user);

        sendVerificationEmail(user);

        return AuthResponse.builder()
                .message("Verification email resent. Please check your inbox.")
                .build();
    }

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


    /**
     * Creates a 24-hour verification token and emails it to the user.
     * Configure {@code spring.mail.*} in {@code application.properties}.
     */
    private void sendVerificationEmail(User user) {
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(UUID.randomUUID());
        verificationToken.setUser(user);
        verificationToken.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.save(verificationToken);

        String token = verificationToken.getToken().toString();
        log.debug("[EMAIL] Verification token for {}: {}", user.getEmail(), token);
        emailService.sendVerificationEmail(user.getEmail(), token);
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