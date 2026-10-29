package com.tujang.studio.domain;

import java.time.LocalDateTime;

/**
 * 用户账号（不含密码哈希）。
 *
 * <p>密码哈希只出现在 {@link UserCredential} 里，并且只在登录校验的那一行读取——
 * 让"能代表用户的对象"和"能验证密码的对象"分开，避免哈希随着 DTO / 日志到处流动。
 */
public record AppUser(
        String id,
        String username,
        String displayName,
        LocalDateTime createdAt,
        LocalDateTime lastSeenAt) {
}
