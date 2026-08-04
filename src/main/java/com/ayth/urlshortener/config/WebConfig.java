package com.ayth.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final UserRateLimitInterceptor userRateLimitInterceptor;
    private final IpRateLimitInterceptor ipRateLimitInterceptor;
    private final QrRateLimitInterceptor qrRateLimitInterceptor;

    public WebConfig(UserRateLimitInterceptor userRateLimitInterceptor,
                     IpRateLimitInterceptor ipRateLimitInterceptor,
                     QrRateLimitInterceptor qrRateLimitInterceptor) {
        this.userRateLimitInterceptor = userRateLimitInterceptor;
        this.ipRateLimitInterceptor = ipRateLimitInterceptor;
        this.qrRateLimitInterceptor = qrRateLimitInterceptor;
    }

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(true);
        filter.setIncludePayload(false);
        filter.setMaxPayloadLength(1000);
        return filter;
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        // Enforce 10 req/min for authenticated actions
        registry.addInterceptor(userRateLimitInterceptor)
                .addPathPatterns(
                        "/create",
                        "/urls/my-urls",
                        "/urls/*/stats",
                        "/urls/*"
                );

        // Enforce 3 req/min for auth endpoints to prevent spam
        registry.addInterceptor(ipRateLimitInterceptor)
                .addPathPatterns(
                        "/register", 
                        "/resend-verification",
                        "/login",
                        "/reset-password",
                        "/forgot-password"
                );

        // Enforce 20 req/min for QR code generation
        registry.addInterceptor(qrRateLimitInterceptor)
                .addPathPatterns("/*/qr");
    }

}
