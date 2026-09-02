package com.ayth.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


class IpRateLimitInterceptorTest {

    private final RateLimitingService rateLimitingService = new RateLimitingService();
    private final IpRateLimitInterceptor interceptor = new IpRateLimitInterceptor(rateLimitingService);

    private static MockHttpServletRequest requestFrom(String remoteAddr, String spoofedForwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (spoofedForwardedFor != null) {
            request.addHeader("X-Forwarded-For", spoofedForwardedFor);
        }
        return request;
    }

    @Test
    void preHandle_withinBurstCapacity_allowsAndLeavesResponseUntouched() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(requestFrom("203.0.113.1", null), response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void preHandle_burstExhausted_returns429WithMessage() throws Exception {
        String realIp = "203.0.113.2";
        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(requestFrom(realIp, null), new MockHttpServletResponse(), new Object());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(requestFrom(realIp, null), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).isEqualTo("Too many requests. Please try again later.");
    }

    @Test
    void preHandle_spoofingADifferentForwardedForOnEveryRequest_stillGetsBlocked() throws Exception {

        String realIp = "203.0.113.3";
        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(
                    requestFrom(realIp, "spoofed-" + i + ".0.0.1"), new MockHttpServletResponse(), new Object());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(
                requestFrom(realIp, "yet-another-spoofed-value"), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void preHandle_differentRealClients_haveIndependentLimits() throws Exception {
        String clientA = "203.0.113.4";
        String clientB = "203.0.113.5";

        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(requestFrom(clientA, null), new MockHttpServletResponse(), new Object());
        }
        assertThat(interceptor.preHandle(requestFrom(clientA, null), new MockHttpServletResponse(), new Object()))
                .isFalse();

        assertThat(interceptor.preHandle(requestFrom(clientB, null), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }
}
