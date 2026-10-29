package com.tujang.studio.web;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tujang.studio.auth.CurrentUserHolder;
import com.tujang.studio.domain.AppUser;
import com.tujang.studio.service.UserService;
import com.tujang.studio.support.UserCookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 身份拦截器：把 Cookie 解析成用户，写进请求属性与 ThreadLocal。
 *
 * <p>为什么用拦截器而不是在每个 Controller 里解析：身份是横切关注点，
 * 放到一处之后，AssetService / TaskService 只依赖 {@code CurrentUserProvider}，
 * 对 HTTP 一无所知。
 *
 * <p>{@code afterCompletion} 一定清 ThreadLocal——Tomcat 线程会被复用，
 * 不清就会把上一个人的身份带给下一个请求。
 */
@Component
public class CurrentUserInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = "tujang.currentUser";

    private final UserService userService;

    public CurrentUserInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserService.Resolution resolution = userService.resolveOrCreate(UserCookie.read(request));
        if (resolution.issuedCookie()) {
            // 首次访问或 Cookie 非法：把服务端新建的 id 发回去
            response.addCookie(UserCookie.of(resolution.user().id()));
        }
        CurrentUserHolder.set(resolution.user().id());
        request.setAttribute(ATTR_USER, resolution.user());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserHolder.clear();
    }

    public static AppUser userOf(HttpServletRequest request) {
        Object user = request.getAttribute(ATTR_USER);
        if (user instanceof AppUser appUser) {
            return appUser;
        }
        throw new IllegalStateException("当前请求没有用户上下文");
    }
}
