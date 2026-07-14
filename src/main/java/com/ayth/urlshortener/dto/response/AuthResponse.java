package com.ayth.urlshortener.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String message;

    private UserDto user;

    @Getter
    @Builder
    public static class UserDto {

        private Long id;

        private String username;

        private String email;
    }
}