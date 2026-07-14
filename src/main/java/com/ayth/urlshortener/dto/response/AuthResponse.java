package com.ayth.urlshortener.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // omit null fields (e.g. accessToken on register)
public class AuthResponse {

    private String message;

    /** Present on successful login; null on register and email verification. */
    private String accessToken;

    private UserDto user;

    @Getter
    @Builder
    public static class UserDto {

        private UUID id;
        private String username;
        private String email;
        private boolean emailVerified;
    }
}