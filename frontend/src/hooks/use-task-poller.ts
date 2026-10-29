"use client";

import { useEffect, useMemo } from "react";

import { ApiError } from "@/lib/api";
import { fetchTask } from "@/lib/tasks";
import { activeTaskIds, useTaskStore } from "@/store/task-store";

/**
 * 任务轮询器。
 *
 * <p>三条纪律：
 * <ul>
 *   <li>只轮询"未完成"的 id 集合——任务进终态就从集合里消失；</li>
 *   <li>集合空 → 清定时器，不再发请求（不是"定时器还在跑但跳过分支"）；</li>
 *   <li>查询失败只标记 queryState，**不碰 task.status**，用户看到的仍是"处理中 + 刷新失败"。</li>
 * </ul>
 *
 * <p>id 集合作为 effect 依赖：集合一变就重建定时器并 abort 上一轮的请求，
 * 因此不会出现旧请求把新状态覆盖回去。
 */
export function useTaskPoller(intervalMs = 1500) {
  const entries = useTaskStore((state) => state.entries);
  const activeKey = useMemo(() => activeTaskIds(entries).join(","), [entries]);

  useEffect(() => {
    if (!activeKey) {
      return;
    }
    const ids = activeKey.split(",");
    const controller = new AbortController();
    let stopped = false;

    const poll = async () => {
      await Promise.all(
        ids.map(async (id) => {
          try {
            const task = await fetchTask(id, controller.signal);
            if (!stopped) {
              useTaskStore.getState().applyTask(task);
            }
          } catch (err) {
            if (stopped || controller.signal.aborted) {
              return;
            }
            const message = err instanceof ApiError ? err.message : "状态刷新失败";
            useTaskStore.getState().markQueryError(id, message);
          }
        }),
      );
    };

    void poll();
    const timer = setInterval(() => void poll(), intervalMs);

    return () => {
      stopped = true;
      clearInterval(timer);
      controller.abort();
    };
  }, [activeKey, intervalMs]);
}
