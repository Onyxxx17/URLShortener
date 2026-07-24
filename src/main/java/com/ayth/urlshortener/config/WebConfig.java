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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173")
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
