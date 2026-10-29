"use client";

import Link from "next/link";
import { useState } from "react";

import { ApiError } from "@/lib/api";
import { login, register } from "@/lib/auth";
import { validateAuthForm } from "@/lib/auth-rules";

type Mode = "login" | "register";

const COPY: Record<Mode, { title: string; subtitle: string; submit: string; switchText: string; switchHref: string; switchLabel: string }> = {
  login: {
    title: "登录",
    subtitle: "登录后可以上传素材、提交制作任务，并查看自己的记录。",
    submit: "登录",
    switchText: "还没有账号？",
    switchHref: "/register",
    switchLabel: "注册一个",
  },
  register: {
    title: "注册",
    subtitle: "创建一个账号，之后用它登录就能找回自己的制作记录。",
    submit: "注册并登录",
    switchText: "已经有账号？",
    switchHref: "/login",
    switchLabel: "去登录",
  },
};

/**
 * 登录 / 注册表单。
 *
 * <p>成功后**整页跳转**而不是 router.push：登录改变了服务端渲染的前提
 * （顶栏、历史页都依赖身份），整页跳转能保证拿到的都是新数据，
 * 也不会把上一个身份的客户端状态留在内存里。
 */
export function AuthForm({ mode, next }: { mode: Mode; next?: string }) {
  const copy = COPY[mode];

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const target = next && next.startsWith("/") ? next : "/";

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setFormError(null);

    const errors = validateAuthForm(username, password);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      if (mode === "login") {
        await login(username.trim(), password);
      } else {
        await register(username.trim(), password);
      }
      window.location.href = target;
    } catch (err) {
      // 服务端错误（用户名被占用 / 密码不对 / 字段校验）都要能落到对应位置
      if (err instanceof ApiError) {
        if (err.fields) {
          setFieldErrors({
            username: err.fields.username,
            password: err.fields.password,
          });
        }
        setFormError(err.message);
      } else {
        setFormError("网络异常，请稍后重试");
      }
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-2xl font-semibold tracking-tight text-white">{copy.title}</h1>
      <p className="mt-2 text-sm leading-relaxed text-zinc-400">{copy.subtitle}</p>

      <form onSubmit={(event) => void handleSubmit(event)} className="card mt-6 space-y-4 p-5" noValidate>
        <div>
          <label htmlFor="username" className="mb-1.5 block text-xs text-zinc-400">
            用户名
          </label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            autoFocus
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            aria-invalid={fieldErrors.username ? true : undefined}
            aria-describedby={fieldErrors.username ? "username-error" : undefined}
            className="w-full rounded-lg border border-white/10 bg-ink-900 px-3 py-2 text-sm text-zinc-100 outline-none placeholder:text-zinc-600 focus:border-accent/60"
            placeholder="字母、数字或下划线，3~24 位"
          />
          {fieldErrors.username && (
            <p id="username-error" className="mt-1.5 text-[11px] text-red-300">
              {fieldErrors.username}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="password" className="mb-1.5 block text-xs text-zinc-400">
            密码
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={fieldErrors.password ? true : undefined}
            aria-describedby={fieldErrors.password ? "password-error" : undefined}
            className="w-full rounded-lg border border-white/10 bg-ink-900 px-3 py-2 text-sm text-zinc-100 outline-none placeholder:text-zinc-600 focus:border-accent/60"
            placeholder="至少 8 位"
          />
          {fieldErrors.password && (
            <p id="password-error" className="mt-1.5 text-[11px] text-red-300">
              {fieldErrors.password}
            </p>
          )}
        </div>

        {formError && (
          <p role="alert" className="rounded-lg bg-red-500/10 px-3 py-2 text-xs leading-relaxed text-red-300">
            {formError}
          </p>
        )}

        <button type="submit" disabled={submitting} className="btn-primary w-full disabled:opacity-60">
          {submitting ? "处理中…" : copy.submit}
        </button>

        <p className="pt-1 text-center text-xs text-zinc-500">
          {copy.switchText}{" "}
          <Link
            href={next ? `${copy.switchHref}?next=${encodeURIComponent(target)}` : copy.switchHref}
            className="text-accent-soft hover:underline"
          >
            {copy.switchLabel}
          </Link>
        </p>
      </form>

      <p className="mt-4 text-center text-xs text-zinc-600">
        <Link href="/" className="hover:text-zinc-400">
          先随便逛逛模板
        </Link>
      </p>
    </div>
  );
}
