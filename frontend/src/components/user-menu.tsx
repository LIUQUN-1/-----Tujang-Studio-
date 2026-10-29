"use client";

import { useState } from "react";

import { logout } from "@/lib/auth";
import type { Me } from "@/lib/types";

/**
 * 当前登录用户与退出入口。
 *
 * <p>退出后整页跳转：服务端渲染的顶栏、历史页都依赖身份，重载一次最省事，
 * 也顺手丢掉内存里属于这个人的任务状态。
 */
export function UserMenu({ user }: { user: Me }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const doLogout = async () => {
    setBusy(true);
    setError(null);
    try {
      await logout();
      window.location.href = "/";
    } catch {
      setBusy(false);
      setError("退出失败，请重试");
    }
  };

  return (
    <div className="flex items-center gap-2">
      <span
        className="hidden items-center gap-1.5 rounded-full border border-white/10 px-2.5 py-1 text-xs text-zinc-400 sm:inline-flex"
        title={`账号：${user.username}`}
      >
        <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-accent" />
        {user.displayName}
      </span>

      <button
        type="button"
        onClick={() => void doLogout()}
        disabled={busy}
        className="rounded-lg px-2.5 py-1.5 text-xs text-zinc-500 transition-colors hover:bg-white/[0.06] hover:text-zinc-200 disabled:opacity-50"
        title={`退出当前账号（${user.username}）`}
      >
        {busy ? "退出中…" : "退出登录"}
      </button>

      {error && <span className="text-xs text-red-300">{error}</span>}
    </div>
  );
}
