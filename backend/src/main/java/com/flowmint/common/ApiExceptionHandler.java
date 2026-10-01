package com.flowmint.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps failures to {@code {"error", "message", "fields"?}} JSON. Never echoes request bodies or stack traces. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String VALIDATION_FAILED = "Request validation failed";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.status(), e.code(), e.getMessage(), e.fields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        return body(400, VALIDATION_ERROR, VALIDATION_FAILED, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> invalidParameters(HandlerMethodValidationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().stream().findFirst().ifPresent(error -> fields.putIfAbsent(name == null ? "parameter" : name, error.getDefaultMessage()));
        });
        return body(400, VALIDATION_ERROR, VALIDATION_FAILED, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> constraint(ConstraintViolationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return body(400, VALIDATION_ERROR, VALIDATION_FAILED, fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return body(400, VALIDATION_ERROR, "Invalid value for " + e.getName(), Map.of(e.getName(), "Invalid value"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> missingParameter(MissingServletRequestParameterException e) {
        return body(400, VALIDATION_ERROR, "Missing parameter " + e.getParameterName(), Map.of(e.getParameterName(), "Required"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(400, VALIDATION_ERROR, "Malformed request body", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e) {
        return body(404, "NOT_FOUND", "Not found", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
        return body(405, "METHOD_NOT_ALLOWED", "Method not allowed", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        if (e instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            return body(framework.getStatusCode().value(), "BAD_REQUEST", "Request could not be processed", Map.of());
        }
        log.error("Unhandled API error: {}", e.getClass().getName(), e);
        return body(500, "INTERNAL_ERROR", "Something went wrong", Map.of());
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String code, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        if (fields != null && !fields.isEmpty()) body.put("fields", fields);
        return ResponseEntity.status(status).body(body);
    }
}
