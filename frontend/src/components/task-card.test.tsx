import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TaskCard } from "@/components/task-card";
import { ApiError } from "@/lib/api";
import type { TaskDto } from "@/lib/types";
import { useTaskStore } from "@/store/task-store";

vi.mock("@/lib/tasks", () => ({
  fetchTask: vi.fn(),
}));

const { fetchTask } = await import("@/lib/tasks");
const fetchTaskMock = vi.mocked(fetchTask);

function task(overrides: Partial<TaskDto> = {}): TaskDto {
  return {
    id: "task-1",
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

/**
 * 按真实数据流渲染：store 是唯一数据源，卡片从 store 读 entry。
 * 若直接传一个固定 entry，重试成功后卡片不会重渲染，测出来的行为是假的。
 */
function StoreBackedCards() {
  const entries = useTaskStore((state) => state.entries);
  const order = useTaskStore((state) => state.order);
  return (
    <>
      {order.map((id) => (
        <TaskCard key={id} entry={entries[id]} showTemplate />
      ))}
    </>
  );
}

function renderCards(dto: TaskDto, queryError?: string) {
  useTaskStore.getState().upsertMany([dto]);
  if (queryError) {
    useTaskStore.getState().markQueryError(dto.id, queryError);
  }
  return render(<StoreBackedCards />);
}

/** 阶段标签的语义状态由 aria-current 暴露，测试只认这个，不依赖样式类名。 */
function activeStep(): string | null {
  const current = screen.getAllByRole("listitem").find((li) => li.querySelector("[aria-current]"));
  return current?.querySelector("[aria-current]")?.textContent ?? null;
}

beforeEach(() => {
  fetchTaskMock.mockReset();
  useTaskStore.getState().reset();
});

describe("TaskCard", () => {
  it("处理中：徽标显示处理中，且只有一个阶段被标为当前", () => {
    renderCards(task({ status: "PROCESSING", currentStep: "GENERATE" }));

    expect(screen.getByText("处理中")).toBeInTheDocument();
    expect(activeStep()).toBe("模拟生成");
    expect(screen.getByText("结果整理")).toBeInTheDocument();
    expect(screen.queryByText(/结果图/)).not.toBeInTheDocument();
  });

  it("等待处理：没有任何阶段被标为当前（阶段条不能假装已开始）", () => {
    renderCards(task({ status: "PENDING", currentStep: null }));

    expect(screen.getByText("等待处理")).toBeInTheDocument();
    expect(activeStep()).toBeNull();
  });

  it("成功：渲染结果缩略图与尺寸，阶段条不再有当前项", () => {
    renderCards(
      task({
        status: "SUCCEEDED",
        currentStep: "FINALIZE",
        finishedAt: "2026-10-29T01:00:08.000Z",
        result: {
          url: "/files/results/demo-postcard-1.jpg",
          width: 1200,
          height: 900,
          createdAt: "2026-10-29T01:00:08.000Z",
        },
      }),
    );

    expect(screen.getByText("已完成")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "旅行明信片制作结果" })).toHaveAttribute(
      "src",
      "/files/results/demo-postcard-1.jpg",
    );
    expect(screen.getByText(/结果图 · 1200×900/)).toBeInTheDocument();
    expect(activeStep()).toBeNull();
  });

  it("失败：写明失败阶段与原因，且没有结果图", () => {
    renderCards(
      task({
        status: "FAILED",
        currentStep: "GENERATE",
        failedStep: "GENERATE",
        errorCode: "GENERATE_TIMEOUT",
        errorMessage: "生成服务响应超时，请稍后重试",
      }),
    );

    expect(screen.getByText("失败")).toBeInTheDocument();
    expect(screen.getByText(/在「模拟生成」失败：/)).toBeInTheDocument();
    expect(screen.getByText(/生成服务响应超时/)).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: /制作结果/ })).not.toBeInTheDocument();
  });

  it("查询异常：只加一条黄色提示，不把任务标成失败", () => {
    renderCards(task({ status: "PROCESSING", currentStep: "GENERATE" }), "网络连接中断");

    // 任务本身的状态表达保持原样
    expect(screen.getByText("处理中")).toBeInTheDocument();
    expect(screen.queryByText("失败")).not.toBeInTheDocument();
    expect(activeStep()).toBe("模拟生成");

    // 查询通道单独提示，并说明两者无关
    expect(screen.getByText(/状态刷新失败：网络连接中断/)).toBeInTheDocument();
    expect(screen.getByText("任务本身未受影响")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
  });

  it("任务失败与查询异常同时发生时，两条提示各自保留", () => {
    renderCards(
      task({ status: "FAILED", currentStep: "GENERATE", failedStep: "GENERATE", errorMessage: "生成失败" }),
      "网络连接中断",
    );

    expect(screen.getByText(/在「模拟生成」失败：/)).toBeInTheDocument();
    expect(screen.getByText(/状态刷新失败：网络连接中断/)).toBeInTheDocument();
    // 查询异常不会叠出第二个"失败"徽标
    expect(screen.getAllByText("失败")).toHaveLength(1);
  });

  it("点重试拿到新状态后，提示消失且卡片推进", async () => {
    fetchTaskMock.mockResolvedValue(task({ status: "PROCESSING", currentStep: "FINALIZE" }));

    renderCards(task({ status: "PROCESSING", currentStep: "GENERATE" }), "网络连接中断");
    fireEvent.click(screen.getByRole("button", { name: "重试" }));

    await waitFor(() => {
      expect(screen.queryByText(/状态刷新失败/)).not.toBeInTheDocument();
    });
    expect(fetchTaskMock).toHaveBeenCalledWith("task-1");
    // store 是唯一数据源：重试成功后阶段推进到结果整理
    expect(activeStep()).toBe("结果整理");
    expect(useTaskStore.getState().entries["task-1"].queryState).toBe("ok");
  });

  it("重试仍然失败时保留提示，并换成服务端给的原因", async () => {
    fetchTaskMock.mockRejectedValue(new ApiError(503, "SERVICE_UNAVAILABLE", "服务暂时不可用"));

    renderCards(task({ status: "PROCESSING" }), "网络连接中断");
    fireEvent.click(screen.getByRole("button", { name: "重试" }));

    await waitFor(() => {
      expect(screen.getByText(/状态刷新失败：服务暂时不可用/)).toBeInTheDocument();
    });
    // 查询失败依旧不改任务状态
    expect(useTaskStore.getState().entries["task-1"].task.status).toBe("PROCESSING");
  });
});
