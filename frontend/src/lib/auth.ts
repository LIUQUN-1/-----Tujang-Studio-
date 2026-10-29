import { apiPost } from "./api";
import type { Me } from "./types";

/**
 * 注册并自动登录（后端 201 + Set-Cookie）。
 *
 * <p>Cookie 是 HttpOnly 的，前端拿不到也不需要拿：登录成功后整页跳转，
 * 之后的服务端渲染与接口请求都会带上它。
 */
export function register(username: string, password: string): Promise<Me> {
  return apiPost<Me>("/api/auth/register", { username, password });
}

/** 登录（后端 200 + Set-Cookie）。失败时 ApiError.message 可直接展示。 */
export function login(username: string, password: string): Promise<Me> {
  return apiPost<Me>("/api/auth/login", { username, password });
}

/** 退出：服务端删会话、清 Cookie。 */
export function logout(): Promise<void> {
  return apiPost<void>("/api/auth/logout", {});
}
