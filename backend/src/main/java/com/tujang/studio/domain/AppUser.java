package com.tujang.studio.domain;

import java.time.LocalDateTime;

/** 应用用户。只承担"记录归属"，不承担登录/密码——身份就是一个 Cookie。 */
public record AppUser(
        String id,
        String displayName,
        LocalDateTime createdAt,
        LocalDateTime lastSeenAt) {
}
