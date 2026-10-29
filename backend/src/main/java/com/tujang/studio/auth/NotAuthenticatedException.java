package com.tujang.studio.auth;

/**
 * 需要登录才能继续。
 *
 * <p>由 {@link CurrentUserProvider} 在"取当前用户但没人登录"时抛出，
 * {@code GlobalExceptionHandler} 统一转成 {@code 401 UNAUTHORIZED}。
 * 业务层（AssetService / TaskService）不必自己判断登录状态——它们只会在
 * 受保护接口里被调用，拿不到人就是 401。
 */
public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException() {
        super("请先登录");
    }
}
