import { apiGet, apiPost, serverGet } from "./api";
import type { TaskDto } from "./types";

/** 提交制作任务：后端返回 202，任务处于 PENDING，之后由前端轮询推进。 */
export function submitTask(templateId: string, assetIds: string[]): Promise<TaskDto> {
  return apiPost<TaskDto>("/api/tasks", { templateId, assetIds });
}

/** 查询单个任务（浏览器侧，走同源代理）。 */
export function fetchTask(id: string, signal?: AbortSignal): Promise<TaskDto> {
  return apiGet<TaskDto>(`/api/tasks/${encodeURIComponent(id)}`, { signal });
}

/** 浏览器侧任务列表（刷新恢复用）。 */
export function listTasks(limit = 50): Promise<TaskDto[]> {
  return apiGet<TaskDto[]>(`/api/tasks?limit=${limit}`);
}

/** 服务端组件用：首屏渲染历史/初始列表。 */
export function listTasksServer(limit = 50): Promise<TaskDto[]> {
  return serverGet<TaskDto[]>(`/api/tasks?limit=${limit}`);
}
