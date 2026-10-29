import { apiPost } from "./api";
import type { Me } from "./types";

/**
 * 演示用：切换身份（仅 dev 后端提供，生产返回 404）。
 *
 * @param userId 传了就切回这个用户，不传则换成一个全新用户
 */
export function switchUser(userId?: string): Promise<Me> {
  return apiPost<Me>("/api/dev/users/switch", userId ? { userId } : {});
}
