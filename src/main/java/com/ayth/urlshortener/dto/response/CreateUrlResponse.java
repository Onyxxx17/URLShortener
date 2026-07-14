package com.ayth.urlshortener.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class CreateUrlResponse {
    private String shortUrl;
    private String shortCode;
    private String originalUrl;
    private Instant createdAt;
    private Instant expiresAt;
    private Long clickCount;
    private Long id;
    private String createdBy;
}
