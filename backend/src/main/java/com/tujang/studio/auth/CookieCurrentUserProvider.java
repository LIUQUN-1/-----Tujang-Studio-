package com.tujang.studio.auth;

import org.springframework.stereotype.Component;

/**
 * 从请求上下文取当前用户（由 {@code CurrentUserInterceptor} 写入）。
 *
 * <p>P2 起业务代码就只依赖 {@link CurrentUserProvider}，所以接真实身份时
 * 只需要把 Spring 容器里的实现换掉，AssetService / TaskService 一行都不用改。
 */
@Component
public class CookieCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String currentUserId() {
        return CurrentUserHolder.get();
    }
}
