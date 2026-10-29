"use client";

import { useState } from "react";

import { ApiError } from "@/lib/api";
import { fetchTask } from "@/lib/tasks";
import type { TaskDto, TaskStatus, TaskStepCode } from "@/lib/types";
import { useTaskStore, type TaskEntry } from "@/store/task-store";

const STATUS_META: Record<TaskStatus, { label: string; badge: string }> = {
  PENDING: { label: "等待处理", badge: "border-white/15 bg-white/[0.04] text-zinc-300" },
  PROCESSING: { label: "处理中", badge: "border-accent/50 bg-accent/10 text-accent-soft" },
  SUCCEEDED: { label: "已完成", badge: "border-emerald-400/40 bg-emerald-400/10 text-emerald-300" },
  FAILED: { label: "失败", badge: "border-red-400/40 bg-red-400/10 text-red-300" },
};

const STEP_ORDER: TaskStepCode[] = ["ASSET_CHECK", "GENERATE", "FINALIZE"];
const STEP_LABEL: Record<TaskStepCode, string> = {
  ASSET_CHECK: "素材检查",
  GENERATE: "模拟生成",
  FINALIZE: "结果整理",
};

type StepState = "done" | "active" | "failed" | "pending";

function stepStateOf(task: TaskDto, step: TaskStepCode): StepState {
  if (task.status === "FAILED" && task.failedStep === step) return "failed";
  if (task.status === "SUCCEEDED") return "done";
  const currentIndex = task.currentStep ? STEP_ORDER.indexOf(task.currentStep) : -1;
  const index = STEP_ORDER.indexOf(step);
  if (currentIndex < 0) return "pending";
  if (index < currentIndex) return "done";
  if (index === currentIndex) return "active";
  return "pending";
}

export function TaskCard({ entry, showTemplate }: { entry: TaskEntry; showTemplate?: boolean }) {
  const { task, queryState, queryError } = entry;
  const meta = STATUS_META[task.status];
  const [retrying, setRetrying] = useState(false);

  const retry = async () => {
    setRetrying(true);
    try {
      const fresh = await fetchTask(task.id);
      useTaskStore.getState().applyTask(fresh);
    } catch (err) {
      useTaskStore
        .getState()
        .markQueryError(task.id, err instanceof ApiError ? err.message : "状态刷新失败");
    } finally {
      setRetrying(false);
    }
  };

  return (
    <article className="rounded-xl border border-white/[0.07] bg-ink-850/60 p-3">
      {showTemplate && (
        <div className="mb-2.5 flex items-center gap-2">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={task.templateCoverUrl}
            alt=""
            width={28}
            height={35}
            className="h-9 w-7 rounded-md object-cover"
          />
          <span className="truncate text-[13px] text-zinc-300">{task.templateTitle}</span>
        </div>
      )}

      <header className="flex items-center justify-between gap-2">
        <span className={`rounded-full border px-2 py-0.5 text-[11px] ${meta.badge}`}>{meta.label}</span>
        <time className="text-[11px] text-zinc-500" dateTime={task.createdAt} suppressHydrationWarning>
          {formatTime(task.createdAt)}
        </time>
      </header>

      <ol className="mt-2.5 flex items-center gap-1.5" aria-label="处理阶段">
        {STEP_ORDER.map((step) => {
          const state = stepStateOf(task, step);
          return (
            <li key={step} className="flex items-center gap-1.5">
              <span
                className={`rounded px-1.5 py-0.5 text-[11px] ${
                  state === "done"
                    ? "bg-emerald-400/10 text-emerald-300"
                    : state === "active"
                      ? "bg-accent/15 text-accent-soft"
                      : state === "failed"
                        ? "bg-red-400/15 text-red-300"
                        : "bg-white/[0.03] text-zinc-600"
                }`}
                aria-current={state === "active" ? "step" : undefined}
              >
                {STEP_LABEL[step]}
              </span>
              {step !== "FINALIZE" && (
                <span aria-hidden className="text-[10px] text-zinc-700">
                  →
                </span>
              )}
            </li>
          );
        })}
      </ol>

      {task.result && (
        <div className="mt-3 flex items-start gap-3">
          <a
            href={task.result.url}
            target="_blank"
            rel="noreferrer"
            className="shrink-0 overflow-hidden rounded-lg border border-white/10"
            aria-label={`查看${task.templateTitle}的结果大图`}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={task.result.url}
              alt={`${task.templateTitle}制作结果`}
              width={112}
              height={84}
              className="h-[84px] w-28 object-cover"
            />
          </a>
          <div className="min-w-0 flex-1 text-[11px] leading-relaxed text-zinc-500">
            <p>
              结果图
              {task.result.width && task.result.height
                ? ` · ${task.result.width}×${task.result.height}`
                : ""}
            </p>
            <p className="mt-0.5 text-zinc-600">点击缩略图查看原图</p>
          </div>
        </div>
      )}

      {task.status === "FAILED" && (
        <p className="mt-2.5 rounded-lg bg-red-500/[0.06] px-2.5 py-2 text-[11px] leading-relaxed text-red-300">
          {task.failedStep ? `在「${STEP_LABEL[task.failedStep]}」失败：` : ""}
          {task.errorMessage ?? "制作失败"}
        </p>
      )}

      {/* 查询异常与任务失败是两件事，视觉上也分开 */}
      {queryState === "error" && (
        <div className="mt-2.5 flex items-center justify-between gap-2 rounded-lg bg-amber-400/[0.07] px-2.5 py-2">
          <p className="text-[11px] leading-relaxed text-amber-200/90">
            状态刷新失败{queryError ? `：${queryError}` : ""}
            <span className="ml-1 text-amber-200/60">任务本身未受影响</span>
          </p>
          <button
            type="button"
            onClick={() => void retry()}
            disabled={retrying}
            className="shrink-0 rounded-md bg-white/10 px-2 py-1 text-[11px] text-zinc-100 hover:bg-white/20 disabled:opacity-50"
          >
            {retrying ? "刷新中" : "重试"}
          </button>
        </div>
      )}
    </article>
  );
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}
