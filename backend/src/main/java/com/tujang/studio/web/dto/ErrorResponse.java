package com.tujang.studio.web.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一错误体（docs/api.md §0.5）。{@code fields} 只在字段级校验错误时出现。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Map<String, String> fields) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    public static ErrorResponse of(String code, String message, Map<String, String> fields) {
        return new ErrorResponse(code, message, fields);
    }
}
