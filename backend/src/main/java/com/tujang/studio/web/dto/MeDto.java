package com.tujang.studio.web.dto;

import java.time.Instant;

/**
 * 当前身份（docs/api.md §5.1）。
 *
 * @param devTools 当前后端是否跑在 dev profile：前端据此显示"切换用户"等开发入口，
 *                 生产环境这些入口不应出现
 */
public record MeDto(String userId, String displayName, Instant createdAt, boolean devTools) {
}
