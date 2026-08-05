package com.ayth.urlshortener.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final ConcurrentHashMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> qrIpBuckets = new ConcurrentHashMap<>();

    // 10 requests per minute for authenticated users
    private final Bandwidth userLimit = Bandwidth.classic(100, Refill.intervally(10, Duration.ofMinutes(1)));

    // 3 requests per minute per IP for public endpoints 
    private final Bandwidth ipLimit = Bandwidth.classic(10, Refill.intervally(3, Duration.ofMinutes(1)));

    // 20 requests per minute per IP for QR code generation
    private final Bandwidth qrIpLimit = Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1)));

    public Bucket resolveUserBucket(String userId) {
        return userBuckets.computeIfAbsent(userId, key -> Bucket.builder()
                .addLimit(userLimit)
                .build());
    }

    public Bucket resolveIpBucket(String ipAddress) {
        return ipBuckets.computeIfAbsent(ipAddress, key -> Bucket.builder()
                .addLimit(ipLimit)
                .build());
    }

    public Bucket resolveQrIpBucket(String ipAddress) {
        return qrIpBuckets.computeIfAbsent(ipAddress, key -> Bucket.builder()
                .addLimit(qrIpLimit)
                .build());
    }
}
