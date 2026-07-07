package com.ayth.urlshortener.exception;

import com.ayth.urlshortener.dto.response.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestControllerAdvice
class GlobalExceptionHandler {

    // ---- 404: URL not found ----
    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUrlNotFound(UrlNotFoundException ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(
                404, "Not Found", ex.getMessage(), getPath(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ---- 409: URL already exists ----
    @ExceptionHandler(UrlAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUrlAlreadyExists(UrlAlreadyExistsException ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(
                409, "Conflict", ex.getMessage(), getPath(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ---- 410: URL has expired ----
    @ExceptionHandler(UrlExpiredException.class)
    public ResponseEntity<ErrorResponse> handleUrlExpired(UrlExpiredException ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(
                410, "Gone", ex.getMessage(), getPath(request));
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

    // ---- 400: Validation errors (@Valid failed) ----
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage(),
                        fe.getRejectedValue()))
                .toList();

        ErrorResponse body = ErrorResponse.ofValidation(
                "Validation failed", getPath(request), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ---- 400: Malformed JSON body ----
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(
                400, "Bad Request", "Malformed JSON or missing request body", getPath(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ---- 409: DB constraint violation (e.g. duplicate short code) ----
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(
                409, "Conflict", "A database constraint was violated (possible duplicate entry)", getPath(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ---- 500: Catch-all for anything unexpected ----
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        ErrorResponse body = ErrorResponse.of(
                500, "Internal Server Error", "An unexpected error occurred", getPath(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String getPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}
