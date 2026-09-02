package com.ayth.urlshortener.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class QrRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    @Autowired
    public QrRateLimitInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ipAddress = ClientIpResolver.resolveClientIp(request);
        Bucket bucket = rateLimitingService.resolveQrIpBucket(ipAddress);

        if (bucket.tryConsume(1)) {
            return true;
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests from this IP for QR codes. Please try again later.");
            return false;
        }
    }
}
