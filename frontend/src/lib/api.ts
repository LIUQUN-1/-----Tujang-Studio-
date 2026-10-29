/** 客户端取数（浏览器侧）。服务端取数见 `server-api.ts`。 */

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

/**
 * 浏览器侧 GET。统一走同源相对路径，由 next.config.ts 的 rewrites 转发到 Java，
 * 因此这里**不需要** baseURL 或 CORS 处理，Cookie 也会被浏览器自动带上。
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

/** 浏览器侧 JSON POST。 */
export async function apiPost<T>(path: string, body: unknown, signal?: AbortSignal): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    body: JSON.stringify(body),
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    signal,
  });
  if (!res.ok) {
    throw await toApiError(res);
  }
  // 204 没有响应体（例如退出登录），此时 res.json() 会抛错
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

/** 浏览器侧 multipart 上传。不要手工设置 Content-Type，交给浏览器带 boundary。 */
export async function apiUpload<T>(path: string, form: FormData, signal?: AbortSignal): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    body: form,
    headers: { Accept: "application/json" },
    signal,
  });
  if (!res.ok) {
    throw await toApiError(res);
  }
  return (await res.json()) as T;
}
