package com.tujang.studio.web.dto;

/** 注册（docs/api.md §5.2）。字段校验在 AuthService 里做，错误按字段返回。 */
public record RegisterRequest(String username, String password) {
}
