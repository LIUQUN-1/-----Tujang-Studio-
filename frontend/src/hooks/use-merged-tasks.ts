"use client";

import { useMemo } from "react";

import type { TaskDto } from "@/lib/types";
import { useTaskStore, type TaskEntry } from "@/store/task-store";

export type MergedTask = {
  task: TaskDto;
  entry: TaskEntry;
};

/**
 * 合并"服务端首屏数据"与"客户端 store"：
 * - 首帧用服务端数据，刷新后不必等网络就能看到内容；
 * - 同一 id 以 store 为准（轮询拿到的是更新的）；
 * - 结果按创建时间倒序。
 *
 * 只做纯计算，不在 effect 里写 store——避免首帧空白和"旧数据覆盖新数据"。
 */
export function useMergedTasks(initialTasks: TaskDto[], templateId?: string): MergedTask[] {
  const entries = useTaskStore((state) => state.entries);
  const order = useTaskStore((state) => state.order);

  return useMemo(() => {
    const merged = new Map<string, TaskDto>();
    for (const task of initialTasks) {
      merged.set(task.id, task);
    }
    for (const id of order) {
      const entry = entries[id];
      if (entry) {
        merged.set(id, entry.task);
      }
    }

    return [...merged.values()]
      .filter((task) => (templateId ? task.templateId === templateId : true))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((task) => ({
        task,
        entry: entries[task.id] ?? { task, queryState: "ok" as const },
      }));
  }, [entries, order, initialTasks, templateId]);
}
