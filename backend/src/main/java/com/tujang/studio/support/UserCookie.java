package com.tujang.studio.support;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** 身份 Cookie 的名字与构造，集中一处，避免拦截器与切换接口写出两份不一致的属性。 */
public final class UserCookie {

    public static final String NAME = "tujang_uid";
    private static final int MAX_AGE_SECONDS = (int) Duration.ofDays(30).toSeconds();

    private UserCookie() {
    }

    /**
     * 构造身份 Cookie。
     *
     * <p>{@code HttpOnly} 防脚本读取，{@code SameSite=Lax} 防跨站带上；
     * 本地 http 下不能加 {@code Secure}，否则浏览器不会发送——生产部署时应开启，见 README。
     */
    public static Cookie of(String userId) {
        Cookie cookie = new Cookie(NAME, userId);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
