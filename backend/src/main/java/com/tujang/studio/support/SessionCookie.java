package com.tujang.studio.support;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** 会话 Cookie 的名字与构造，集中一处，避免登录/退出/拦截器写出三份不一致的属性。 */
public final class SessionCookie {

    public static final String NAME = "tujang_sid";
    private static final int MAX_AGE_SECONDS = (int) Duration.ofDays(30).toSeconds();

    private SessionCookie() {
    }

    /**
     * 构造会话 Cookie。
     *
     * <p>{@code HttpOnly} 让脚本读不到（XSS 也偷不走），{@code SameSite=Lax} 挡住跨站请求；
     * 本地 http 下不能加 {@code Secure}，否则浏览器不会发送——生产部署时应开启，见 README。
     */
    public static Cookie of(String token) {
        Cookie cookie = new Cookie(NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    /** 退出登录用：同名、Max-Age=0，浏览器立刻删掉。 */
    public static Cookie cleared() {
        Cookie cookie = new Cookie(NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
