package com.ayth.urlshortener.config;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests — no Spring context. RateLimitingService has no
 * dependencies, so it is constructed directly.
 */
class RateLimitingServiceTest {

    private final RateLimitingService service = new RateLimitingService();

    // ─────────────────────────────────────────────────────────────────────────
    // Burst capacity
    //
    // Bucket4j's "classic" bandwidth starts a bucket FULL at its capacity
    // argument, not at its refill amount. The capacity here is deliberately
    // higher than the refill rate for two of the four buckets — the in-code
    // comments describe only the steady-state rate ("10 requests per minute",
    // "3 requests per minute"), which understates what a client can actually
    // burst through on a brand-new key. These tests pin down the real,
    // observable allowance rather than the commented intent.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resolveUserBucket_burstCapacityIs100_farAboveTheCommentedTenPerMinute() {
        Bucket bucket = service.resolveUserBucket("user-1");

        for (int i = 1; i <= 100; i++) {
            assertThat(bucket.tryConsume(1)).as("request #%d", i).isTrue();
        }
        assertThat(bucket.tryConsume(1)).as("request #101").isFalse();
    }

    @Test
    void resolveIpBucket_burstCapacityIs10_wellAboveTheCommentedThreePerMinute() {
        Bucket bucket = service.resolveIpBucket("1.2.3.4");

        for (int i = 1; i <= 10; i++) {
            assertThat(bucket.tryConsume(1)).as("request #%d", i).isTrue();
        }
        assertThat(bucket.tryConsume(1)).as("request #11").isFalse();
    }

    @Test
    void resolveQrIpBucket_burstCapacityMatchesTheCommentedTwentyPerMinute() {
        Bucket bucket = service.resolveQrIpBucket("1.2.3.4");

        for (int i = 1; i <= 20; i++) {
            assertThat(bucket.tryConsume(1)).as("request #%d", i).isTrue();
        }
        assertThat(bucket.tryConsume(1)).as("request #21").isFalse();
    }

    @Test
    void resolveRedirectIpBucket_burstCapacityMatchesTheCommentedSixtyPerMinute() {
        Bucket bucket = service.resolveRedirectIpBucket("1.2.3.4");

        for (int i = 1; i <= 60; i++) {
            assertThat(bucket.tryConsume(1)).as("request #%d", i).isTrue();
        }
        assertThat(bucket.tryConsume(1)).as("request #61").isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-key isolation and caching
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resolveIpBucket_differentIps_getIndependentBuckets() {
        Bucket exhausted = service.resolveIpBucket("1.1.1.1");
        Bucket untouched = service.resolveIpBucket("2.2.2.2");

        for (int i = 0; i < 10; i++) {
            exhausted.tryConsume(1);
        }

        assertThat(exhausted.tryConsume(1)).isFalse();
        assertThat(untouched.tryConsume(1)).isTrue();
    }

    @Test
    void resolveIpBucket_sameIpAcrossCalls_returnsTheSameCachedBucket() {
        Bucket first = service.resolveIpBucket("9.9.9.9");
        first.tryConsume(10); // exhaust it via the first reference

        Bucket second = service.resolveIpBucket("9.9.9.9");

        // If a new bucket were created per call, this would still have its
        // full 10-token capacity available.
        assertThat(second.tryConsume(1)).isFalse();
    }

    @Test
    void resolveUserBucket_andResolveIpBucket_areIndependentScopesForTheSameKeyString() {
        // The same raw string used as a userId in one call and an IP address in
        // another must not collide — the two are backed by separate maps.
        String sharedKey = "203.0.113.7";
        Bucket ipBucket = service.resolveIpBucket(sharedKey);
        Bucket userBucket = service.resolveUserBucket(sharedKey);

        for (int i = 0; i < 10; i++) {
            ipBucket.tryConsume(1);
        }

        assertThat(ipBucket.tryConsume(1)).isFalse();
        assertThat(userBucket.tryConsume(1)).isTrue();
    }

    @Test
    void resolveQrIpBucket_andResolveRedirectIpBucket_areIndependentScopesForTheSameIp() {
        String ip = "198.51.100.4";
        Bucket qrBucket = service.resolveQrIpBucket(ip);
        Bucket redirectBucket = service.resolveRedirectIpBucket(ip);

        for (int i = 0; i < 20; i++) {
            qrBucket.tryConsume(1);
        }

        assertThat(qrBucket.tryConsume(1)).isFalse();
        assertThat(redirectBucket.tryConsume(1)).isTrue();
    }
}
