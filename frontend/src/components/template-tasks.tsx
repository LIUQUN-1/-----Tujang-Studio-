"use client";

import { useEffect, useMemo, useRef } from "react";

import { TaskCard } from "@/components/task-card";
import { useTaskPoller } from "@/hooks/use-task-poller";
import type { TaskDto } from "@/lib/types";
import { useTaskStore } from "@/store/task-store";

type Props = {
  templateId: string;
  /** 服务端首屏渲染的任务（刷新后也能立刻看到，不必等轮询） */
  initialTasks: TaskDto[];
};

/**
 * 某个模板下的制作记录。
 *
 * <p>展示数据以 store 为准、以服务端首屏数据兜底：首帧就能看到历史，
 * 轮询拿到的更新会逐条覆盖对应 id，不受其他任务影响。
 */
export function TemplateTasks({ templateId, initialTasks }: Props) {
  const entries = useTaskStore((state) => state.entries);
  const order = useTaskStore((state) => state.order);

  useTaskPoller();

  // 把服务端首屏数据灌进 store，让轮询知道哪些任务还需要跟踪
  const hydrated = useRef(false);
  useEffect(() => {
    if (hydrated.current) return;
    hydrated.current = true;
    useTaskStore.getState().upsertMany(initialTasks);
  }, [initialTasks]);

  const tasks = useMemo(() => {
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
      .filter((task) => task.templateId === templateId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [entries, order, initialTasks, templateId]);

  if (tasks.length === 0) {
    return (
      <div className="mt-4 rounded-xl border border-dashed border-white/10 px-4 py-14 text-center">
        <p className="text-sm text-zinc-400">还没有制作记录</p>
        <p className="mx-auto mt-1 max-w-xs text-xs leading-relaxed text-zinc-600">
          提交任务后，处理进度和结果会出现在这里；离开页面或刷新都不会中断后台处理。
        </p>
      </div>
    );
  }

  return (
    <ul className="mt-4 space-y-2.5">
      {tasks.map((task) => {
        const entry = entries[task.id];
        return (
          <li key={task.id}>
            <TaskCard entry={entry ?? { task, queryState: "ok" }} />
          </li>
        );
      })}
    </ul>
  );
}
