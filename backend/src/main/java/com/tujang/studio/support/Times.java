package com.tujang.studio.support;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 时间约定：库里一律存 UTC 的 {@link LocalDateTime}（MySQL DATETIME 不带时区，
 * 用 LocalDateTime 可避免驱动做二次时区换算），出接口时再转成 {@code Instant} 带上 Z。
 */
public final class Times {

    private Times() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
