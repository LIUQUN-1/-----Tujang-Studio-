"use client";

import Link from "next/link";
import { useState } from "react";

import { UploadSlot } from "@/components/upload-slot";
import { useUploadSlots } from "@/hooks/use-upload-slots";
import { ApiError } from "@/lib/api";
import { submitTask } from "@/lib/tasks";
import type { TemplateDetail } from "@/lib/types";
import { useTaskStore } from "@/store/task-store";

/**
 * 制作区：N 个上传位 + 提交入口。
 *
 * <p>提交成功后：把任务塞进 store（右侧立即出现卡片并开始轮询）、清空上传位。
 * 素材不上传服务端删除——它们已经属于这个任务了。
 *
 * <p>未登录时整块换成登录入口：素材归属到账号，匿名上传没有意义，
 * 与其让用户传完再被 401 打回，不如提前说清楚。
 */
export function TemplateComposer({
  template,
  authed,
}: {
  template: TemplateDetail;
  authed: boolean;
}) {
  const { slots, readyCount, isUploading, allReady, pick, remove, retry, reset } =
    useUploadSlots(template);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const accept = template.acceptedMimeTypes.join(",");
  const canSubmit = allReady && !isUploading && !submitting;

  if (!authed) {
    return (
      <div className="mt-4 rounded-xl border border-dashed border-white/10 px-4 py-8 text-center">
        <p className="text-sm text-zinc-300">登录后开始制作</p>
        <p className="mx-auto mt-1 max-w-xs text-xs leading-relaxed text-zinc-600">
          上传的素材和提交的任务都会记在你的账号下，登录后可以随时回来看进度和结果。
        </p>
        <div className="mt-4 flex items-center justify-center gap-2">
          <Link
            href={`/login?next=${encodeURIComponent(`/templates/${template.id}`)}`}
            className="btn-primary px-4 py-2 text-xs"
          >
            登录
          </Link>
          <Link
            href={`/register?next=${encodeURIComponent(`/templates/${template.id}`)}`}
            className="btn-ghost px-4 py-2 text-xs"
          >
            注册
          </Link>
        </div>
      </div>
    );
  }


  const handleSubmit = async () => {
    setSubmitError(null);
    setNotice(null);
    setSubmitting(true);
    try {
      const assetIds = slots
        .map((slot) => slot.assetId)
        .filter((id): id is string => typeof id === "string");
      const task = await submitTask(template.id, assetIds);
      useTaskStore.getState().upsertMany([task]);
      reset();
      setNotice("已提交，任务正在后台处理，进度见右侧");
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : "提交失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mt-4">
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="text-sm font-medium text-zinc-300">素材</h2>
        <p aria-live="polite" className="text-[11px] text-zinc-500">
          已就绪 {readyCount} / {template.requiredImageCount}
        </p>
      </div>

      <div className={`grid gap-2.5 ${template.requiredImageCount === 1 ? "grid-cols-1" : "grid-cols-2"}`}>
        {slots.map((slot) => (
          <UploadSlot
            key={slot.key}
            slot={slot}
            accept={accept}
            onPick={(key, file) => void pick(key, file)}
            onRemove={remove}
            onRetry={retry}
          />
        ))}
      </div>

      <button
        type="button"
        onClick={() => void handleSubmit()}
        disabled={!canSubmit}
        className="btn-primary mt-4 w-full"
        aria-describedby="submit-hint"
      >
        {submitting ? "提交中…" : isUploading ? "素材上传中…" : "提交制作"}
      </button>

      <p id="submit-hint" className="mt-2 text-center text-[11px] text-zinc-600">
        {allReady ? "素材已就绪，提交后可在右侧跟踪进度" : `还需上传 ${template.requiredImageCount - readyCount} 张素材`}
      </p>

      <div aria-live="polite">
        {notice && <p className="mt-1.5 text-center text-[11px] text-accent-soft">{notice}</p>}
        {submitError && <p className="mt-1.5 text-center text-[11px] text-red-300">{submitError}</p>}
      </div>
    </div>
  );
}
