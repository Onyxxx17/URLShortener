package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;
import com.ayth.urlshortener.email.EmailService;
import com.ayth.urlshortener.email.EmailVerificationToken;
import com.ayth.urlshortener.email.EmailVerificationTokenRepository;
import com.ayth.urlshortener.email.PasswordResetToken;
import com.ayth.urlshortener.email.PasswordResetTokenRepository;
import com.ayth.urlshortener.exception.EmailNotVerifiedException;
import com.ayth.urlshortener.exception.InvalidCredentialsException;
import com.ayth.urlshortener.exception.UserAlreadyExistsException;
import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for AuthService.
 * No Spring context — all collaborators are Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock EmailService emailService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks AuthService authService;

    private User verifiedUser;
    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        verifiedUser = new User();
        verifiedUser.setId(UUID.randomUUID());
        verifiedUser.setEmail("verified@example.com");
        verifiedUser.setUsername("verified");
        verifiedUser.setPassword("$2a$10$encodedPassword");
        verifiedUser.setEmailVerified(true);

        unverifiedUser = new User();
        unverifiedUser.setId(UUID.randomUUID());
        unverifiedUser.setEmail("unverified@example.com");
        unverifiedUser.setUsername("unverified");
        unverifiedUser.setPassword("$2a$10$encodedPassword");
        unverifiedUser.setEmailVerified(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // register
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void register_success_savesUserAndSendsEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@example.com");
        req.setUsername("newuser");
        req.setPassword("password123");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(req);

        assertThat(response.getMessage())
                .contains("Registration successful");
        verify(emailService).sendVerificationEmail(eq("new@example.com"), anyString());
    }

    @Test
    void register_duplicateEmail_throwsUserAlreadyExistsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("verified@example.com");
        req.setUsername("other");
        req.setPassword("password123");

        when(userRepository.existsByEmail("verified@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void register_duplicateUsername_throwsUserAlreadyExistsException() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("brand@new.com");
        req.setUsername("verified");
        req.setPassword("password123");

        when(userRepository.existsByEmail("brand@new.com")).thenReturn(false);
        when(userRepository.existsByUsername("verified")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void login_validVerifiedUser_returnsTokenAndUser() {
        LoginRequest req = new LoginRequest();
        req.setEmail("verified@example.com");
        req.setPassword("password123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new UserPrincipal(verifiedUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("mock.jwt.token");

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getMessage()).contains("Login successful");
        assertThat(response.getUser().getEmail()).isEqualTo("verified@example.com");
    }

    @Test
    void login_emailNotVerified_throwsEmailNotVerifiedException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("unverified@example.com");
        req.setPassword("password123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new UserPrincipal(unverifiedUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        LoginRequest req = new LoginRequest();
        req.setEmail("verified@example.com");
        req.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verifyEmail
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void verifyEmail_validToken_verifiesUser() {
        UUID tokenId = UUID.randomUUID();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenId);
        token.setUser(unverifiedUser);
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(tokenRepository.findByToken(tokenId)).thenReturn(Optional.of(token));
        when(userRepository.save(unverifiedUser)).thenReturn(unverifiedUser);
        when(tokenRepository.save(token)).thenReturn(token);

        authService.verifyEmail(tokenId.toString());

        assertThat(unverifiedUser.isEmailVerified()).isTrue();
        verify(userRepository).save(unverifiedUser);
    }

    @Test
    void verifyEmail_alreadyUsedToken_throwsInvalidCredentialsException() {
        UUID tokenId = UUID.randomUUID();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenId);
        token.setUser(unverifiedUser);
        token.setUsedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(tokenRepository.findByToken(tokenId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(tokenId.toString()))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void verifyEmail_expiredToken_throwsInvalidCredentialsException() {
        UUID tokenId = UUID.randomUUID();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenId);
        token.setUser(unverifiedUser);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(tokenRepository.findByToken(tokenId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(tokenId.toString()))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyEmail_malformedToken_throwsInvalidCredentialsException() {
        assertThatThrownBy(() -> authService.verifyEmail("not-a-uuid"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid verification token format");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMe
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getMe_withPrincipal_returnsUserDto() {
        UserPrincipal principal = new UserPrincipal(verifiedUser);

        AuthResponse.UserDto dto = authService.getMe(principal);

        assertThat(dto.getEmail()).isEqualTo("verified@example.com");
        assertThat(dto.isEmailVerified()).isTrue();
    }

    @Test
    void getMe_nullPrincipal_throwsAuthenticationCredentialsNotFoundException() {
        assertThatThrownBy(() -> authService.getMe(null))
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resendVerificationEmail
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resendVerification_unknownEmail_returnsGenericMessage() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        AuthResponse response = authService.resendVerificationEmail("unknown@example.com");

        assertThat(response.getMessage()).contains("If that email is registered");
        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerification_alreadyVerified_returnsAlreadyVerifiedMessage() {
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verifiedUser));

        AuthResponse response = authService.resendVerificationEmail("verified@example.com");

        assertThat(response.getMessage()).contains("already verified");
        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerification_unverifiedUser_deletesOldTokensAndSendsNew() {
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.resendVerificationEmail("unverified@example.com");

        assertThat(response.getMessage()).contains("resent");
        verify(tokenRepository).deleteAllByUser(unverifiedUser);
        verify(emailService).sendVerificationEmail(eq("unverified@example.com"), anyString());
    }
}
