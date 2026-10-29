package com.tujang.studio.web;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带稳定错误码与面向用户的文案，由 {@link GlobalExceptionHandler} 统一转成
 * docs/api.md §0.5 约定的错误体。
 *
 * <p>不把 Java 堆栈暴露给前端——{@code message} 是给用户看的。
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, String> fields() {
        return fields;
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, null);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, null);
    }

    public static ApiException invalid(String message, Map<String, String> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, fields);
    }
}
