package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;
import jakarta.servlet.http.HttpSession;
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
            @RequestBody @Valid LoginRequest request,
            HttpSession session
    ) {
        System.out.println("Login endpoint hit");
        return authService.login(request, session);
    }


    @PostMapping("/logout")
    public String logout(
            HttpSession session
    ) {
        authService.logout(session);
        return "Logged out";
    }

}
