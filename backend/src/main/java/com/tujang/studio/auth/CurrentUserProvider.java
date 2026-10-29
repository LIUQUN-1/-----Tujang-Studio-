package com.tujang.studio.auth;

/**
 * 当前用户 id 的取用口。
 *
 * <p>业务代码（AssetService / TaskService）只依赖这个接口，对 HTTP、Cookie、
 * 会话表一无所知。P2 用固定演示用户、P6 用身份 Cookie、P9 换成登录会话，
 * 这里换了两次实现，业务代码一行没动。
 */
public interface CurrentUserProvider {

    /**
     * 当前登录用户。
     *
     * @throws NotAuthenticatedException 没有登录（由全局异常处理转成 401）
     */
    String currentUserId();
}
