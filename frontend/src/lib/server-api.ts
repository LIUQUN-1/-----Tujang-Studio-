import { cookies } from "next/headers";

import { ApiError } from "./api";
import type { Category, Me, TaskDto, TemplateDetail, TemplateSummary } from "./types";

/**
 * **只能被服务端组件使用**的取数入口。
 *
 * <p>两件与客户端取数不同的事：
 * <ol>
 *   <li>直连 Java（不经过自身代理），省一次跳转；</li>
 *   <li>**转发浏览器的 Cookie**——否则后端会把每个 SSR 请求当成新访客，
 *       历史页会看不到自己的任务。</li>
 * </ol>
 */
export function serverApiBase(): string {
  return (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

async function cookieHeader(): Promise<string> {
  const store = await cookies();
  return store
    .getAll()
    .map((cookie) => `${cookie.name}=${cookie.value}`)
    .join("; ");
}

async function serverGet<T>(path: string): Promise<T> {
  const url = `${serverApiBase()}${path}`;
  const cookie = await cookieHeader();

  let res: Response;
  try {
    res = await fetch(url, {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        ...(cookie ? { Cookie: cookie } : {}),
      },
    });
  } catch {
    throw new ApiError(
      0,
      "BACKEND_UNREACHABLE",
      `无法连接后端服务（${serverApiBase()}），请确认 Java 已启动`,
    );
  }

  if (!res.ok) {
    let code = `HTTP_${res.status}`;
    let message = `请求失败（${res.status}）`;
    try {
      const body = (await res.json()) as { code?: string; message?: string };
      code = body.code ?? code;
      message = body.message ?? message;
    } catch {
      // 非 JSON 错误体
    }
    throw new ApiError(res.status, code, message);
  }

  return (await res.json()) as T;
}

/**
 * 当前登录用户。**未登录返回 null，不抛错**：
 * 大部分页面在匿名状态下也要正常渲染（浏览模板），把 401 当成异常到处 try/catch 更糟。
 */
export async function getMe(): Promise<Me | null> {
  try {
    return await serverGet<Me>("/api/me");
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      return null;
    }
    throw err;
  }
}

export function getCategories(): Promise<Category[]> {
  return serverGet<Category[]>("/api/categories");
}

export function getTemplates(category?: string): Promise<TemplateSummary[]> {
  const query = category ? `?category=${encodeURIComponent(category)}` : "";
  return serverGet<TemplateSummary[]>(`/api/templates${query}`);
}

export function getTemplate(id: string): Promise<TemplateDetail> {
  return serverGet<TemplateDetail>(`/api/templates/${encodeURIComponent(id)}`);
}

export function listTasksServer(limit = 50): Promise<TaskDto[]> {
  return serverGet<TaskDto[]>(`/api/tasks?limit=${limit}`);
}
