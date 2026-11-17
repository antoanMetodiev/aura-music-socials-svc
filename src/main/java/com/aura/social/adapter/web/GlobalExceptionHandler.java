package com.aura.social.adapter.web;

import com.aura.social.config.RequestIdFilter;
import com.aura.social.domain.service.SocialExceptions;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception the social API can throw into the {@code {code, message, traceId}} shape
 * from Project-Info.md §49. Never forwards a stack trace to the client (§35, §49).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(String code, String message, String traceId) {
    }

    @ExceptionHandler(SocialExceptions.UserNotFound.class)
    public ResponseEntity<ApiError> userNotFound(SocialExceptions.UserNotFound e) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noRoute(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint");
    }

    @ExceptionHandler(SocialExceptions.Conflict.class)
    public ResponseEntity<ApiError> conflict(SocialExceptions.Conflict e) {
        return error(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler(SocialExceptions.LimitExceeded.class)
    public ResponseEntity<ApiError> limit(SocialExceptions.LimitExceeded e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "LIMIT_EXCEEDED", e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        String traceId = MDC.get(RequestIdFilter.MDC_KEY);
        return ResponseEntity.status(status).body(new ApiError(code, message, traceId));
    }
}
