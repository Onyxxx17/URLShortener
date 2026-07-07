package com.ayth.urlshortener.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public record FieldError(
            String field,
            String message,
            Object rejectedValue
    ) {
    }

    /**
     * Quick factory for simple errors (no field-level details).
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    /**
     * Factory for validation errors with per-field details.
     */
    public static ErrorResponse ofValidation(String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), 400, "Bad Request", message, path, fieldErrors);
    }
}
