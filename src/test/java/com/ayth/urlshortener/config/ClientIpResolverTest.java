package com.ayth.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void resolveClientIp_noForwardedHeader_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");

        assertThat(ClientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void resolveClientIp_spoofedForwardedHeader_isIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.1.1.1");

        assertThat(ClientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void resolveClientIp_differentForwardedHeaderPerRequest_stillResolvesToTheSameRealAddress() {
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setRemoteAddr("203.0.113.9");
        first.addHeader("X-Forwarded-For", "1.1.1.1");

        MockHttpServletRequest second = new MockHttpServletRequest();
        second.setRemoteAddr("203.0.113.9");
        second.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8");

        assertThat(ClientIpResolver.resolveClientIp(first))
                .isEqualTo(ClientIpResolver.resolveClientIp(second));
    }

    @Test
    void resolveClientIp_multiHopForwardedHeader_isIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3");

        assertThat(ClientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void resolveClientIp_emptyForwardedHeader_isIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "");

        assertThat(ClientIpResolver.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }
}
