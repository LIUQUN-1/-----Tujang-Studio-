package com.tujang.studio.web.dto;

import java.time.Instant;

/**
 * 当前登录用户（docs/api.md §5.1）。
 *
 * @param username 登录名，界面展示用
 * @param devTools 后端是否跑在 dev profile：前端据此显示"模拟控制"这类开发入口
 */
public record MeDto(
        String userId,
        String username,
        String displayName,
        Instant createdAt,
        boolean devTools) {
}
