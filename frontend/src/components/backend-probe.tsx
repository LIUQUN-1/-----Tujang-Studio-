"use client";

import { useEffect, useState } from "react";

import { apiGet, type HealthOk } from "@/lib/api";

type Probe =
  | { state: "loading" }
  | { state: "ok"; data: HealthOk }
  | { state: "error"; message: string };

/**
 * 在**浏览器**里请求 /api/health，验证 Next 的 rewrite 代理链路。
 * 这一步和 page.tsx 的服务端直连是两条独立路径，都通才算骨架搭好。
 */
export function BackendProbe() {
  const [probe, setProbe] = useState<Probe>({ state: "loading" });

  useEffect(() => {
    let alive = true;
    apiGet<HealthOk>("/api/health")
      .then((data) => {
        if (alive) setProbe({ state: "ok", data });
      })
      .catch((err: unknown) => {
        if (alive) {
          setProbe({ state: "error", message: err instanceof Error ? err.message : "未知错误" });
        }
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <div className="flex items-center gap-3 text-sm">
      <StatusDot state={probe.state} />
      <div>
        <p className="font-medium text-zinc-200">浏览器 → Next 代理 → Java</p>
        <p className="text-xs text-zinc-500">
          {probe.state === "loading" && "请求 /api/health…"}
          {probe.state === "ok" && `HTTP 200 · ${probe.data.app} · ${probe.data.time}`}
          {probe.state === "error" && `失败：${probe.message}`}
        </p>
      </div>
    </div>
  );
}

function StatusDot({ state }: { state: Probe["state"] }) {
  const color =
    state === "ok" ? "bg-accent" : state === "error" ? "bg-red-400" : "bg-amber-300 animate-pulse";
  return <span aria-hidden className={`h-2.5 w-2.5 shrink-0 rounded-full ${color}`} />;
}
