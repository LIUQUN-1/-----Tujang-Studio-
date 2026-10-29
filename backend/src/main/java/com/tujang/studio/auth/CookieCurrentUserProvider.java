package com.tujang.studio.auth;

import org.springframework.stereotype.Component;

/**
 * 从请求上下文取当前用户（由 {@code CurrentUserInterceptor} 在会话校验通过后写入）。
 *
 * <p>没登录时 {@link CurrentUserHolder#get()} 会抛 {@link NotAuthenticatedException}，
 * 由全局异常处理转成 401——所以 AssetService / TaskService 完全不需要写"是否登录"的分支。
 */
@Component
public class CookieCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String currentUserId() {
        return CurrentUserHolder.get();
    }
}
