package com.ayth.urlshortener.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserRateLimitInterceptor userRateLimitInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;

    @Autowired
    public WebMvcConfig(UserRateLimitInterceptor userRateLimitInterceptor, IpRateLimitInterceptor ipRateLimitInterceptor) {
        this.userRateLimitInterceptor = userRateLimitInterceptor;
        this.ipRateLimitInterceptor = ipRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Enforce 10 req/min for URL creation (User)
        registry.addInterceptor(userRateLimitInterceptor)
                .addPathPatterns("/create");

        // Enforce 3 req/min for auth endpoints to prevent spam
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns("/register", "/resend-verification","/login");
    }
}
