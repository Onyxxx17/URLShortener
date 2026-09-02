package com.ayth.urlshortener.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the IP address a rate-limit bucket should be keyed on.
 */
final class ClientIpResolver {

    private ClientIpResolver() {
    }

    static String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
