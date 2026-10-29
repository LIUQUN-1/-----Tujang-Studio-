package com.tujang.studio.domain;

/** 登录校验专用的用户凭据：只有它带密码哈希。 */
public record UserCredential(
        String id,
        String username,
        String passwordHash) {
}
