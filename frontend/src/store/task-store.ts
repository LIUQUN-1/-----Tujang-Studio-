"use client";

import { create } from "zustand";

import type { TaskDto } from "@/lib/types";

/** 查询通道状态。与 task.status 完全独立：查不到 ≠ 任务失败。 */
export type QueryState = "ok" | "error";

export type TaskEntry = {
  task: TaskDto;
  queryState: QueryState;
  queryError?: string;
};

type TaskStore = {
  entries: Record<string, TaskEntry>;
  /** 最近创建在前 */
  order: string[];
  /** 批量写入（首屏历史、刷新恢复）：视为查询成功 */
  upsertMany: (tasks: TaskDto[]) => void;
  /** 只写入 store 里还没有的 id：用于首屏兜底，不覆盖已从网络拿到的新数据 */
  hydrateMissing: (tasks: TaskDto[]) => void;
  /** 单条更新（轮询回来）：只覆盖 task，不动其他任务 */
  applyTask: (task: TaskDto) => void;
  /** 查询失败：**只**标记查询通道，绝不改写 task.status */
  markQueryError: (id: string, message: string) => void;
  reset: () => void;
};

/**
 * 任务状态按 **id 索引**，这是"多任务互不串扰"的结构性保证：
 * 不存在一个全局的"当前任务状态"，也就不会出现"第二个任务一提交，第一个状态被覆盖"。
 */
export const useTaskStore = create<TaskStore>((set) => ({
  entries: {},
  order: [],

  upsertMany: (tasks) =>
    set((state) => {
      const entries = { ...state.entries };
      const order = [...state.order];
      for (const task of tasks) {
        entries[task.id] = { task, queryState: "ok", queryError: undefined };
        if (!order.includes(task.id)) {
          order.push(task.id);
        }
      }
      order.sort((a, b) => (entries[b]?.task.createdAt ?? "").localeCompare(entries[a]?.task.createdAt ?? ""));
      return { entries, order };
    }),

  applyTask: (task) =>
    set((state) => ({
      entries: { ...state.entries, [task.id]: { task, queryState: "ok", queryError: undefined } },
      order: state.order.includes(task.id) ? state.order : [task.id, ...state.order],
    })),

  hydrateMissing: (tasks) =>
    set((state) => {
      const missing = tasks.filter((task) => !state.entries[task.id]);
      if (missing.length === 0) {
        return state;
      }
      const entries = { ...state.entries };
      const order = [...state.order];
      for (const task of missing) {
        entries[task.id] = { task, queryState: "ok" };
        if (!order.includes(task.id)) {
          order.push(task.id);
        }
      }
      order.sort((a, b) => (entries[b]?.task.createdAt ?? "").localeCompare(entries[a]?.task.createdAt ?? ""));
      return { entries, order };
    }),

  markQueryError: (id, message) =>
    set((state) => {
      const entry = state.entries[id];
      if (!entry) {
        return state;
      }
      return {
        entries: { ...state.entries, [id]: { ...entry, queryState: "error", queryError: message } },
      };
    }),

  reset: () => set({ entries: {}, order: [] }),
}));

/** 需要继续轮询的任务 id：等待中或处理中。 */
export function activeTaskIds(entries: Record<string, TaskEntry>): string[] {
  return Object.values(entries)
    .filter((entry) => entry.task.status === "PENDING" || entry.task.status === "PROCESSING")
    .map((entry) => entry.task.id)
    .sort();
}
