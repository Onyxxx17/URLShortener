package com.ayth.urlshortener.config;



import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title("URL Shortener API")
                                .version("1.0")
                                .description(
                                        "REST API for creating and managing shortened URLs"
                                )
                                .contact(
                                        new Contact()
                                                .name("Aung Ye Thant Hein")
                                                .email("aungyethant@gmail.com")
                                )
                );
    }
}