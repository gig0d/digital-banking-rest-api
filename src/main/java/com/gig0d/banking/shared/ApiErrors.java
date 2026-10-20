package com.gig0d.banking.shared;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:banking:problem:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail domain(ApiException ex) { return problem(ex.status(), ex.code(), ex.getMessage()); }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, MissingRequestValueException.class})
    ProblemDetail invalid(Exception ex) {
        // Never echo rejected values: these can include passwords and private customer data.
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request contains invalid or unsupported values.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden() {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "This operation is not permitted.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraint() {
        return problem(HttpStatus.CONFLICT, "CONSTRAINT_CONFLICT", "The request conflicts with an existing resource or business invariant.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail missing() { return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "The resource was not found."); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(ex.getSupportedHttpMethods() == null ? new org.springframework.http.HttpMethod[0]
                        : ex.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new))
                .body(problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This resource does not support the requested method."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail mediaType() {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use application/json for request bodies.");
    }

    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<ProblemDetail> contention() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "1")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_REQUEST", "Temporary database contention. Retry with the same idempotency key."));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        String reference = UUID.randomUUID().toString();
        LOG.error("Unexpected failure reference={} type={}", reference, ex.getClass().getSimpleName());
        ProblemDetail result = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
        result.setProperty("reference", reference);
        return result;
    }
}
