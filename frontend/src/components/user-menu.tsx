"use client";

import { useEffect, useState } from "react";

import { switchUser } from "@/lib/me";
import type { Me } from "@/lib/types";

const PREVIOUS_USER_KEY = "tujang.previousUserId";

/**
 * 当前身份与开发用的身份切换。
 *
 * <p>两条入口：
 * <ul>
 *   <li>「切换用户」——换一个全新用户，历史随之清空（演示个人记录隔离）；</li>
 *   <li>「切回上一个」——回到切换前的身份，这样隔离演示不必开两个浏览器。</li>
 * </ul>
 *
 * <p>切换后整页跳转：换人意味着所有服务端渲染的数据和客户端缓存都要重来。
 * 两个入口都只在 dev 后端出现（`me.devTools`），生产环境不显示。
 */
export function UserMenu({ user }: { user: Me | null }) {
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previousUserId, setPreviousUserId] = useState<string | null>(null);

  useEffect(() => {
    const stored = window.localStorage.getItem(PREVIOUS_USER_KEY);
    if (stored && user && stored !== user.userId) {
      setPreviousUserId(stored);
    }
  }, [user]);

  if (!user) {
    return null;
  }

  const doSwitch = async (targetId?: string) => {
    setSwitching(true);
    setError(null);
    try {
      // 记住切换前的身份，便于"切回上一个"
      window.localStorage.setItem(PREVIOUS_USER_KEY, user.userId);
      await switchUser(targetId);
      window.location.href = "/";
    } catch {
      setSwitching(false);
      setError("切换失败");
    }
  };

  return (
    <div className="flex items-center gap-2">
      <span
        className="hidden items-center gap-1.5 rounded-full border border-white/10 px-2.5 py-1 text-xs text-zinc-400 sm:inline-flex"
        title={`用户 ID：${user.userId}`}
      >
        <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-accent" />
        {user.displayName}
      </span>

      {user.devTools && (
        <>
          {previousUserId && (
            <button
              type="button"
              onClick={() => void doSwitch(previousUserId)}
              disabled={switching}
              className="rounded-lg px-2.5 py-1.5 text-xs text-zinc-500 transition-colors hover:bg-white/[0.06] hover:text-zinc-200 disabled:opacity-50"
              title="切回切换前的用户"
            >
              切回上一个
            </button>
          )}
          <button
            type="button"
            onClick={() => void doSwitch()}
            disabled={switching}
            className="rounded-lg px-2.5 py-1.5 text-xs text-zinc-500 transition-colors hover:bg-white/[0.06] hover:text-zinc-200 disabled:opacity-50"
            title="演示用：切换到一个全新的用户，个人记录随之清空"
          >
            {switching ? "切换中…" : "切换用户"}
          </button>
        </>
      )}

      {error && <span className="text-xs text-red-300">{error}</span>}
    </div>
  );
}
