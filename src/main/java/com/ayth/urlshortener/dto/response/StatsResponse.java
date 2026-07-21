package com.ayth.urlshortener.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
public class StatsResponse {
    private Long id;
    private String shortCode;
    private String originalUrl;
    private Long clickCount;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastAccessedAt;
    private Long daysUntilExpiry;
    private Boolean isExpired;
    private Long ageInDays;
    private List<ClickEventDto> recentClicks;

    @Getter
    @Builder
    public static class ClickEventDto {
        private Instant clickedAt;
        private String referer;
        private String userAgent;
    }
}
