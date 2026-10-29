package com.tujang.studio.auth;

/**
 * 当前请求的用户 id。
 *
 * <p>用 ThreadLocal 而不是方法参数，是因为它要被 AssetService / TaskService 这类
 * 与 HTTP 无关的类使用。**只在 servlet 请求线程内有效**——后台任务线程不继承它，
 * 也不该继承（任务归属在建行时就定了）。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(String userId) {
        HOLDER.set(userId);
    }

    public static String get() {
        String userId = HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("当前线程没有用户上下文（非 /api 请求或拦截器未生效）");
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
