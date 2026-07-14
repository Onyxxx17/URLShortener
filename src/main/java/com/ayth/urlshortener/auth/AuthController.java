package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
            @RequestBody @Valid LoginRequest request
    ) {
        return authService.login(request);
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
}
