package com.ayth.urlshortener.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/")
class AuthController {

    private final AuthService authService;

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
        
        // Add JWT to an HttpOnly cookie
        Cookie cookie = new Cookie("jwt", authResponse.getAccessToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        response.addCookie(cookie);

        return authResponse;
    }

    @GetMapping("/verify-email")
    public AuthResponse verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return AuthResponse.builder()
                .message("Email verified successfully. You can now log in.")
                .build();
    }

    @PostMapping("/resend-verification")
    public AuthResponse resendVerification(@RequestParam String email) {
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
        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Deletes the cookie
        response.addCookie(cookie);
        return AuthResponse.builder().message("Logged out successfully").build();
    }
}

