import { beforeEach, describe, expect, it } from "vitest";

import type { TaskDto } from "@/lib/types";
import { activeTaskIds, useTaskStore } from "./task-store";

function task(overrides: Partial<TaskDto> & { id: string }): TaskDto {
  return {
    status: "PROCESSING",
    currentStep: "GENERATE",
    failedStep: null,
    errorCode: null,
    errorMessage: null,
    templateId: "demo-postcard",
    templateTitle: "旅行明信片",
    templateCoverUrl: "/files/seed/covers/demo-postcard.jpg",
    result: null,
    inputs: [],
    simulation: null,
    createdAt: "2026-10-29T01:00:00.000Z",
    startedAt: "2026-10-29T01:00:01.000Z",
    finishedAt: null,
    updatedAt: "2026-10-29T01:00:02.000Z",
    ...overrides,
  };
}

beforeEach(() => {
  useTaskStore.getState().reset();
});

describe("多任务互不串扰", () => {
  it("更新任务 A 不会改动任务 B", () => {
    const store = useTaskStore.getState();
    store.upsertMany([task({ id: "a" }), task({ id: "b" })]);

    useTaskStore.getState().applyTask(task({ id: "a", status: "SUCCEEDED", currentStep: "FINALIZE" }));

    const { entries } = useTaskStore.getState();
    expect(entries.a.task.status).toBe("SUCCEEDED");
    expect(entries.b.task.status).toBe("PROCESSING");
    expect(entries.b.task.currentStep).toBe("GENERATE");
  });

  it("一个任务失败不影响另一个任务的结果", () => {
    useTaskStore.getState().upsertMany([task({ id: "a" }), task({ id: "b" })]);

    useTaskStore.getState().applyTask(
      task({ id: "a", status: "FAILED", failedStep: "GENERATE", errorMessage: "生成超时" }),
    );

    const { entries } = useTaskStore.getState();
    expect(entries.a.task.errorMessage).toBe("生成超时");
    expect(entries.b.task.errorMessage).toBeNull();
    expect(entries.b.task.failedStep).toBeNull();
  });

  it("按创建时间倒序排列，新任务在前", () => {
    useTaskStore.getState().upsertMany([
      task({ id: "old", createdAt: "2026-10-29T01:00:00.000Z" }),
      task({ id: "new", createdAt: "2026-10-29T03:00:00.000Z" }),
    ]);

    expect(useTaskStore.getState().order).toEqual(["new", "old"]);
  });
});

describe("查询异常与任务失败是两件事", () => {
  it("查询失败只标记 queryState，任务状态原样保留", () => {
    useTaskStore.getState().upsertMany([task({ id: "a", status: "PROCESSING", currentStep: "GENERATE" })]);

    useTaskStore.getState().markQueryError("a", "网络断了");

    const entry = useTaskStore.getState().entries.a;
    expect(entry.queryState).toBe("error");
    expect(entry.queryError).toBe("网络断了");
    // 关键断言：任务没有被标成 FAILED，阶段也没有被清空
    expect(entry.task.status).toBe("PROCESSING");
    expect(entry.task.currentStep).toBe("GENERATE");
  });

  it("查询恢复后 queryState 回到 ok 并清掉错误", () => {
    useTaskStore.getState().upsertMany([task({ id: "a" })]);
    useTaskStore.getState().markQueryError("a", "网络断了");

    useTaskStore.getState().applyTask(task({ id: "a", status: "SUCCEEDED", currentStep: "FINALIZE" }));

    const entry = useTaskStore.getState().entries.a;
    expect(entry.queryState).toBe("ok");
    expect(entry.queryError).toBeUndefined();
    expect(entry.task.status).toBe("SUCCEEDED");
  });

  it("对未知 id 标记查询失败不会凭空造出一条任务", () => {
    useTaskStore.getState().markQueryError("ghost", "失败");
    expect(useTaskStore.getState().entries.ghost).toBeUndefined();
  });
});

describe("轮询目标", () => {
  it("只包含等待中与处理中的任务", () => {
    useTaskStore.getState().upsertMany([
      task({ id: "p", status: "PENDING", currentStep: null }),
      task({ id: "r", status: "PROCESSING" }),
      task({ id: "ok", status: "SUCCEEDED" }),
      task({ id: "bad", status: "FAILED" }),
    ]);

    expect(activeTaskIds(useTaskStore.getState().entries).sort()).toEqual(["p", "r"]);
  });

  it("全部结束后集合为空（调用方据此停掉定时器）", () => {
    useTaskStore.getState().upsertMany([task({ id: "ok", status: "SUCCEEDED" })]);
    expect(activeTaskIds(useTaskStore.getState().entries)).toEqual([]);
  });
});

describe("首屏兜底", () => {
  it("hydrate 不覆盖已有条目（避免旧的服务端数据盖掉新的轮询结果）", () => {
    useTaskStore.getState().upsertMany([task({ id: "a", status: "SUCCEEDED", currentStep: "FINALIZE" })]);

    useTaskStore.getState().hydrateMissing([task({ id: "a", status: "PENDING", currentStep: null })]);

    expect(useTaskStore.getState().entries.a.task.status).toBe("SUCCEEDED");
  });

  it("hydrate 只在缺失时补入", () => {
    useTaskStore.getState().hydrateMissing([task({ id: "a" }), task({ id: "b" })]);
    expect(Object.keys(useTaskStore.getState().entries).sort()).toEqual(["a", "b"]);
  });
});
