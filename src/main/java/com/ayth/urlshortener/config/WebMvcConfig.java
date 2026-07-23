package com.ayth.urlshortener.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserRateLimitInterceptor userRateLimitInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;
    private final QrRateLimitInterceptor qrRateLimitInterceptor;

    @Autowired
    public WebMvcConfig(UserRateLimitInterceptor userRateLimitInterceptor, 
                        IpRateLimitInterceptor ipRateLimitInterceptor,
                        QrRateLimitInterceptor qrRateLimitInterceptor) {
        this.userRateLimitInterceptor = userRateLimitInterceptor;
        this.ipRateLimitInterceptor = ipRateLimitInterceptor;
        this.qrRateLimitInterceptor = qrRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Enforce 10 req/min for URL creation
        registry.addInterceptor(userRateLimitInterceptor)
                .addPathPatterns("/create");

        // Enforce 3 req/min for auth endpoints to prevent spam
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/register", "/resend-verification","/login");

        // Enforce 20 req/min for QR code generation
        registry.addInterceptor(qrRateLimitInterceptor)
                .addPathPatterns("/*/qr");
    }
}
