package com.tujang.studio.web;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tujang.studio.auth.CurrentUserHolder;
import com.tujang.studio.domain.AppUser;
import com.tujang.studio.service.AuthService;
import com.tujang.studio.support.SessionCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 身份拦截器：把会话 Cookie 解析成用户，写进请求属性与 ThreadLocal。
 *
 * <p><b>解析失败不拦截请求</b>——没登录是正常状态（浏览模板不需要账号）。
 * 需要登录的接口会通过 {@code CurrentUserProvider} 抛 401，那是业务语义；
 * 在这里统一拦掉的话，公开接口就得维护一份白名单，容易漏。
 *
 * <p>{@code afterCompletion} 一定清 ThreadLocal——Tomcat 线程会被复用，
 * 不清就会把上一个人的身份带给下一个请求。
 */
@Component
public class CurrentUserInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = "tujang.currentUser";

    private final AuthService authService;

    public CurrentUserInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        authService.resolveUser(SessionCookie.read(request)).ifPresent(user -> {
            CurrentUserHolder.set(user.id());
            request.setAttribute(ATTR_USER, user);
        });
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserHolder.clear();
    }

    /** 当前登录用户；没登录返回 {@code null}（调用方自己决定 401 还是按匿名处理）。 */
    public static AppUser userOrNull(HttpServletRequest request) {
        Object user = request.getAttribute(ATTR_USER);
        return user instanceof AppUser appUser ? appUser : null;
    }
}
