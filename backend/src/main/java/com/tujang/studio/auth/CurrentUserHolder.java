package com.tujang.studio.auth;

/**
 * 当前请求的用户 id。
 *
 * <p>用 ThreadLocal 而不是方法参数，是因为它要被 AssetService / TaskService 这类
 * 与 HTTP 无关的类使用。**只在 servlet 请求线程内有效**——后台任务线程不继承它，
 * 也不该继承（任务归属在建行时就定了）。
 *
 * <p>没有登录时这里就是空的：受保护接口取 {@link #get()} 会抛
 * {@link NotAuthenticatedException}（→ 401），公开接口不会来取。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(String userId) {
        HOLDER.set(userId);
    }

    /** 当前用户；没人登录时抛 {@link NotAuthenticatedException}。 */
    public static String get() {
        String userId = HOLDER.get();
        if (userId == null) {
            throw new NotAuthenticatedException();
        }
        return userId;
    }

    /** 可能为空的版本：给"登录可选"的地方用（目前没有，但别让调用方写出 try/catch 来猜）。 */
    public static String getOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
