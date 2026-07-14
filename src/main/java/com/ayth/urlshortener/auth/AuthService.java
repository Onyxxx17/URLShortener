package com.ayth.urlshortener.auth;

import com.ayth.urlshortener.dto.request.LoginRequest;
import com.ayth.urlshortener.dto.request.RegisterRequest;
import com.ayth.urlshortener.dto.response.AuthResponse;
import com.ayth.urlshortener.exception.InvalidCredentialsException;
import com.ayth.urlshortener.exception.UserAlreadyExistsException;
import com.ayth.urlshortener.users.User;
import com.ayth.urlshortener.users.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder();

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
        user.setPassword(encoder.encode(request.getPassword()));

        userRepository.save(user);

        return AuthResponse.builder()
                .message("User registered successfully")
                .user(
                        AuthResponse.UserDto.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .build()
                )
                .build();
    }

    public AuthResponse login(
            LoginRequest request,
            HttpSession session
    ) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!encoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new InvalidCredentialsException();
        }

        session.setAttribute("userId", user.getId());

        return AuthResponse.builder()
                .message("Login successful")
                .user(
                        AuthResponse.UserDto.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .build()
                )
                .build();
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }
}