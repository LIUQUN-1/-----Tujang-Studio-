"use client";

import { useEffect } from "react";

/**
 * 段级错误边界：后端不可用、接口报错都会落到这里。
 * 关键是"可恢复"——给出原因和重试入口，而不是白屏。
 */
export default function ErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("[页面错误]", error);
  }, [error]);

  return (
    <div className="card mx-auto mt-6 flex max-w-xl flex-col items-center px-6 py-14 text-center">
      <span aria-hidden className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-red-500/10 text-xl">
        ⚠️
      </span>
      <h1 className="text-base font-medium text-zinc-100">页面没能加载出来</h1>
      <p className="mt-2 max-w-md text-sm leading-relaxed text-zinc-400">
        {error.message || "发生了未知错误。"}
      </p>
      <p className="mt-1 text-xs text-zinc-600">
        若是后端未启动，请确认 Java 服务与 MySQL 都已就绪。
      </p>
      <button type="button" onClick={reset} className="btn-primary mt-6">
        重新加载
      </button>
    </div>
  );
}
