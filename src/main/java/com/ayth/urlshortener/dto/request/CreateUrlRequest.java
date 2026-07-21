package com.ayth.urlshortener.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
public class CreateUrlRequest {

    @NotNull(message = "URL must not be null")
    @NotBlank(message = "URL must not be blank")
    @URL(message = "Must be a valid URL")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String originalUrl;

    @Min(value = 1, message = "Expiry must be at least 1 day")
    @Max(value = 365, message = "Expiry must not exceed 365 days")
    private Integer expiresInDays;
}
