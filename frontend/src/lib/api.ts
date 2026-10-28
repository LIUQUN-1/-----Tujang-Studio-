export type HealthOk = {
  status: string;
  app: string;
  time: string;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
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
    let code = `HTTP_${res.status}`;
    let message = `请求失败（${res.status}）`;
    try {
      const body = (await res.json()) as { code?: string; message?: string };
      code = body.code ?? code;
      message = body.message ?? message;
    } catch {
      // 非 JSON 错误体，保留默认文案
    }
    throw new ApiError(res.status, code, message);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

/** 服务端组件使用：直接访问 Java，不经过自身代理。 */
export function serverApiBase(): string {
  return (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}
