package com.ayth.urlshortener.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ayth.urlshortener.dto.request.ForgotPasswordRequest;
import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.request.ResetPasswordRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/")
@Validated
class AuthController {

    private final AuthService authService;

    // Lax works for a same-origin deploy; a cross-origin frontend needs "None"
    // (requires Secure, already set below) via APP_COOKIE_SAME_SITE.
    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @RequestBody @Valid RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request);

        ResponseCookie cookie = ResponseCookie.from("jwt", authResponse.getAccessToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(24 * 60 * 60)
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return authResponse;
    }

    @GetMapping("/verify-email")
    public AuthResponse verifyEmail(
            @RequestParam
            @NotBlank(message = "Token is required")
            @Pattern(
                regexp = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$",
                message = "Invalid token format"
            )
            String token) {
        authService.verifyEmail(token);
        return AuthResponse.builder()
                .message("Email verified successfully. You can now log in.")
                .build();
    }

    @PostMapping("/resend-verification")
    public AuthResponse resendVerification(
            @RequestParam
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email) {
        return authService.resendVerificationEmail(email);
    }

    @PostMapping("/forgot-password")
    public AuthResponse forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return authService.requestPasswordReset(request.getEmail());
    }

    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return authService.resetPassword(request.getToken(), request.getNewPassword());
    }

    @GetMapping("/me")
    public AuthResponse.UserDto getMe(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return authService.getMe(userPrincipal);
    }

    @PostMapping("/logout")
    public AuthResponse logout(HttpServletResponse response) {
        // Attributes must match the cookie set on login or the browser won't clear it.
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return AuthResponse.builder().message("Logged out successfully").build();
    }
}

