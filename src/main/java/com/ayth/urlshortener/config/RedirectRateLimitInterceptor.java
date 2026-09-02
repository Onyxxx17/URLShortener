package com.ayth.urlshortener.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RedirectRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    public RedirectRateLimitInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ipAddress = ClientIpResolver.resolveClientIp(request);
        Bucket bucket = rateLimitingService.resolveRedirectIpBucket(ipAddress);

        if (bucket.tryConsume(1)) {
            return true;
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many redirect requests. Please try again later.");
            return false;
        }
    }
}
