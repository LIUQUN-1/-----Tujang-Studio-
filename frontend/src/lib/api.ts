import type { Category, TemplateDetail, TemplateSummary } from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fields?: Record<string, string>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * 浏览器侧统一走同源相对路径，由 next.config.ts 的 rewrites 转发到 Java。
 * 因此这里**不需要**任何 baseURL 或 CORS 处理。
 */
export async function apiGet<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { Accept: "application/json", ...(init?.headers ?? {}) },
    cache: "no-store",
  });

  if (!res.ok) {
    throw await toApiError(res);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

async function toApiError(res: Response): Promise<ApiError> {
  let code = `HTTP_${res.status}`;
  let message = `请求失败（${res.status}）`;
  let fields: Record<string, string> | undefined;
  try {
    const body = (await res.json()) as { code?: string; message?: string; fields?: Record<string, string> };
    code = body.code ?? code;
    message = body.message ?? message;
    fields = body.fields;
  } catch {
    // 非 JSON 错误体（例如网关超时页），保留默认文案
  }
  return new ApiError(res.status, code, message, fields);
}

/** 服务端组件使用：直接访问 Java，不经过自身代理。 */
export function serverApiBase(): string {
  return (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

async function serverGet<T>(path: string): Promise<T> {
  const url = `${serverApiBase()}${path}`;
  let res: Response;
  try {
    res = await fetch(url, { cache: "no-store", headers: { Accept: "application/json" } });
  } catch {
    // 连接被拒 / 超时：包一层，让页面能区分"后端不可用"和"接口报错"
    throw new ApiError(
      0,
      "BACKEND_UNREACHABLE",
      `无法连接后端服务（${serverApiBase()}），请确认 Java 已启动`,
      undefined,
    );
  }
  if (!res.ok) {
    throw await toApiError(res);
  }
  return (await res.json()) as T;
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
