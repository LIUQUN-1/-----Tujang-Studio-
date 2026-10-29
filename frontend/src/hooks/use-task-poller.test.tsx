import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useTaskPoller } from "@/hooks/use-task-poller";
import type { TaskDto } from "@/lib/types";
import { useTaskStore } from "@/store/task-store";

vi.mock("@/lib/tasks", () => ({
  fetchTask: vi.fn(),
}));

const { fetchTask } = await import("@/lib/tasks");
const fetchTaskMock = vi.mocked(fetchTask);

function task(overrides: Partial<TaskDto> & { id: string }): TaskDto {
  return {
    status: "PROCESSING",
    currentStep: "GENERATE",
    failedStep: null,
    errorCode: null,
    errorMessage: null,
    templateId: "demo-postcard",
    templateTitle: "旅行明信片",
    templateCoverUrl: "/cover.jpg",
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

/** 推进假定时器并清空微任务队列。 */
async function tick(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  fetchTaskMock.mockReset();
  useTaskStore.getState().reset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useTaskPoller", () => {
  it("没有待处理任务时不发任何请求", async () => {
    useTaskStore.getState().upsertMany([task({ id: "done", status: "SUCCEEDED" })]);

    renderHook(() => useTaskPoller(true, 1000));
    await tick(5000);

    expect(fetchTaskMock).not.toHaveBeenCalled();
  });

  it("未登录（enabled=false）时既不拉取也不起定时器", async () => {
    useTaskStore.getState().upsertMany([task({ id: "a", status: "PROCESSING" })]);

    renderHook(() => useTaskPoller(false, 1000));
    await tick(5000);

    expect(fetchTaskMock).not.toHaveBeenCalled();
  });

  it("任务进终态后停止轮询（不再发请求）", async () => {
    useTaskStore.getState().upsertMany([task({ id: "a", status: "PROCESSING" })]);
    fetchTaskMock.mockResolvedValue(task({ id: "a", status: "SUCCEEDED", currentStep: "FINALIZE" }));

    renderHook(() => useTaskPoller(true, 1000));
    await tick(0);

    expect(fetchTaskMock).toHaveBeenCalledTimes(1);
    expect(useTaskStore.getState().entries.a.task.status).toBe("SUCCEEDED");

    await tick(5000);
    // 终态已离开活跃集合 → 定时器被清掉，不会再打接口
    expect(fetchTaskMock).toHaveBeenCalledTimes(1);
  });

  it("两个任务各自更新，互不覆盖", async () => {
    useTaskStore.getState().upsertMany([
      task({ id: "a", status: "PROCESSING", currentStep: "GENERATE" }),
      task({ id: "b", status: "PROCESSING", currentStep: "GENERATE" }),
    ]);
    fetchTaskMock.mockImplementation(async (id: string) =>
      id === "a"
        ? task({ id: "a", status: "SUCCEEDED", currentStep: "FINALIZE" })
        : task({ id: "b", status: "PROCESSING", currentStep: "FINALIZE" }),
    );

    renderHook(() => useTaskPoller(true, 1000));
    await tick(0);

    const entries = useTaskStore.getState().entries;
    expect(entries.a.task.status).toBe("SUCCEEDED");
    expect(entries.b.task.status).toBe("PROCESSING");
    expect(entries.b.task.currentStep).toBe("FINALIZE");
  });

  it("查询失败只标记 queryState，任务仍是处理中", async () => {
    useTaskStore.getState().upsertMany([task({ id: "a", status: "PROCESSING", currentStep: "GENERATE" })]);
    fetchTaskMock.mockRejectedValue(new Error("network down"));

    renderHook(() => useTaskPoller(true, 1000));
    await tick(0);

    const entry = useTaskStore.getState().entries.a;
    expect(entry.queryState).toBe("error");
    expect(entry.task.status).toBe("PROCESSING");
    expect(entry.task.currentStep).toBe("GENERATE");
  });

  it("查询失败后仍会继续尝试（任务还没结束）", async () => {
    useTaskStore.getState().upsertMany([task({ id: "a", status: "PROCESSING" })]);
    fetchTaskMock.mockRejectedValueOnce(new Error("blip")).mockResolvedValue(
      task({ id: "a", status: "PROCESSING", currentStep: "FINALIZE" }),
    );

    renderHook(() => useTaskPoller(true, 1000));
    await tick(0);
    expect(useTaskStore.getState().entries.a.queryState).toBe("error");

    await tick(1000);
    const entry = useTaskStore.getState().entries.a;
    expect(entry.queryState).toBe("ok");
    expect(entry.task.currentStep).toBe("FINALIZE");
  });
});

