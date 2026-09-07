package com.ayth.urlshortener.exception;

import com.ayth.urlshortener.dto.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests — no Spring context. Drives each handler method directly
 * with a WebRequest wrapping a real MockHttpServletRequest, asserting both
 * the HTTP status and that the ErrorResponse body never leaks anything
 * beyond the message each handler explicitly sets (see commit 060fd06).
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/some/path");
        request = new ServletWebRequest(mockRequest);
    }

    @Test
    void handleInvalidCredentials_returns401WithExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidCredentials(new InvalidCredentialsException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.error()).isEqualTo("Unauthorized");
        assertThat(body.message()).isEqualTo("Invalid email or password");
        assertThat(body.path()).isEqualTo("/some/path");
    }

    @Test
    void handleUnauthorized_handlesUnauthorizedException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnauthorized(new UnauthorizedException("ignored"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Please login to continue");
    }

    @Test
    void handleUnauthorized_handlesAuthenticationCredentialsNotFoundException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnauthorized(new AuthenticationCredentialsNotFoundException("no creds"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("no creds");
    }

    @Test
    void handleEmailNotVerified_returns403() {
        ResponseEntity<ErrorResponse> response =
                handler.handleEmailNotVerified(new EmailNotVerifiedException("verify first"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("verify first");
    }

    @Test
    void handleUserAlreadyExists_returns409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUserAlreadyExists(new UserAlreadyExistsException("Email"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Email already exists");
    }

    @Test
    void handleUrlNotFound_returns404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUrlNotFound(new UrlNotFoundException("no such url"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("no such url");
    }

    @Test
    void handleUrlAlreadyExists_returns409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUrlAlreadyExists(new UrlAlreadyExistsException("dup"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("dup");
    }

    @Test
    void handleUrlExpired_returns410() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUrlExpired(new UrlExpiredException("expired"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().message()).isEqualTo("expired");
    }

    @Test
    void handleValidation_returns400WithFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "not-an-email", false, null, null, "must be a valid email"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException((org.springframework.core.MethodParameter) null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.fieldErrors()).hasSize(1);
        ErrorResponse.FieldError fieldError = body.fieldErrors().get(0);
        assertThat(fieldError.field()).isEqualTo("email");
        assertThat(fieldError.message()).isEqualTo("must be a valid email");
        assertThat(fieldError.rejectedValue()).isEqualTo("not-an-email");
    }

    @Test
    void handleBadJson_returns400WithGenericMessage_neverLeaksParserDetails() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error at line 42, column 7: unexpected token", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ErrorResponse> response = handler.handleBadJson(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON or missing request body");
    }

    @Test
    void handleDataIntegrity_returns409WithGenericMessage_neverLeaksConstraintName() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"idx_short_code\"");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("A database constraint was violated (possible duplicate entry)");
    }

    @Test
    void handleAccessDenied_returns403WithGenericMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("role check failed"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("You do not have permission to perform this action");
    }

    @Test
    void handleIllegalArgument_returns400WithExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("cannot shorten own domain"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("cannot shorten own domain");
    }

    @Test
    void handleNoResourceFound_returns404WithGenericMessage() {
        NoResourceFoundException ex =
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/swagger-ui.html", null);

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("The requested resource was not found");
    }

    @Test
    void handleGeneric_returns500WithoutLeakingRawExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new NullPointerException("some.internal.field was null"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body.message()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(body.message()).doesNotContain("some.internal.field");
    }

    @Test
    void getPath_fallsBackToRequestDescription_whenNotAServletWebRequest() {
        WebRequest nonServletRequest = new WebRequest() {
            @Override
            public String getHeader(String headerName) { return null; }
            @Override
            public String[] getHeaderValues(String headerName) { return null; }
            @Override
            public java.util.Iterator<String> getHeaderNames() { return java.util.Collections.emptyIterator(); }
            @Override
            public String getParameter(String paramName) { return null; }
            @Override
            public String[] getParameterValues(String paramName) { return null; }
            @Override
            public java.util.Iterator<String> getParameterNames() { return java.util.Collections.emptyIterator(); }
            @Override
            public java.util.Map<String, String[]> getParameterMap() { return java.util.Collections.emptyMap(); }
            @Override
            public java.util.Locale getLocale() { return java.util.Locale.getDefault(); }
            @Override
            public String getContextPath() { return ""; }
            @Override
            public String getRemoteUser() { return null; }
            @Override
            public java.security.Principal getUserPrincipal() { return null; }
            @Override
            public boolean isUserInRole(String role) { return false; }
            @Override
            public boolean isSecure() { return false; }
            @Override
            public boolean checkNotModified(long lastModifiedTimestamp) { return false; }
            @Override
            public boolean checkNotModified(String etag) { return false; }
            @Override
            public boolean checkNotModified(String etag, long lastModifiedTimestamp) { return false; }
            @Override
            public String getDescription(boolean includeClientInfo) { return "uri=/fallback-path"; }
            @Override
            public Object getAttribute(String name, int scope) { return null; }
            @Override
            public void setAttribute(String name, Object value, int scope) { }
            @Override
            public void removeAttribute(String name, int scope) { }
            @Override
            public String[] getAttributeNames(int scope) { return new String[0]; }
            @Override
            public void registerDestructionCallback(String name, Runnable callback, int scope) { }
            @Override
            public Object resolveReference(String key) { return null; }
            @Override
            public String getSessionId() { return null; }
            @Override
            public Object getSessionMutex() { return new Object(); }
        };

        ResponseEntity<ErrorResponse> response =
                handler.handleUrlNotFound(new UrlNotFoundException("gone"), nonServletRequest);

        assertThat(response.getBody().path()).isEqualTo("uri=/fallback-path");
    }
}
