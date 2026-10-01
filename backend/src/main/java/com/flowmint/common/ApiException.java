package com.flowmint.common;

import java.util.Map;

/** An error with a stable code that the API returns as {@code {"error": code, "message": message}}. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(int status, String code, String message) { this(status, code, message, Map.of()); }

    public ApiException(int status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public static ApiException notFound(String message) { return new ApiException(404, "NOT_FOUND", message); }
    public static ApiException conflict(String code, String message) { return new ApiException(409, code, message); }
    public static ApiException validation(String message) { return new ApiException(400, "VALIDATION_ERROR", message); }
    public static ApiException validation(String field, String message) { return new ApiException(400, "VALIDATION_ERROR", message, Map.of(field, message)); }

    public int status() { return status; }
    public String code() { return code; }
    public Map<String, String> fields() { return fields; }
}
