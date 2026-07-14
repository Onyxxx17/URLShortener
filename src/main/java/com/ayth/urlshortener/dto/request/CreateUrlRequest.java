package com.ayth.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

@Getter
@Setter
public class CreateUrlRequest {

    @NotNull(message = "URL must not be null")
    @NotBlank(message = "URL must not be blank")
    @URL(message = "Must be a valid URL")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String originalUrl;

//    @Size(min = 3, max = 20, message = "Custom alias must be between 3 and 20 characters")
//    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Custom alias may only contain letters, numbers, hyphens, and underscores")
//    private String customAlias;

//    private Instant expiresAt;
}
