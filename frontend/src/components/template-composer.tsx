"use client";

import { UploadSlot } from "@/components/upload-slot";
import { useUploadSlots } from "@/hooks/use-upload-slots";
import type { TemplateDetail } from "@/lib/types";

/**
 * 制作区：N 个上传位 + 进度提示 + 提交入口。
 *
 * <p>提交按钮暂时禁用——`POST /api/tasks` 还没接入（P3）。
 * 这里刻意不做一个"假的成功"，避免演示时误导。
 */
export function TemplateComposer({ template }: { template: TemplateDetail }) {
  const { slots, readyCount, isUploading, allReady, pick, remove, retry } = useUploadSlots(template);

  const accept = template.acceptedMimeTypes.join(",");

  return (
    <div className="mt-4">
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="text-sm font-medium text-zinc-300">素材</h2>
        <p aria-live="polite" className="text-[11px] text-zinc-500">
          已就绪 {readyCount} / {template.requiredImageCount}
        </p>
      </div>

      <div
        className={`grid gap-2.5 ${
          template.requiredImageCount === 1 ? "grid-cols-1" : "grid-cols-2"
        }`}
      >
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
        disabled
        className="btn-primary mt-4 w-full"
        aria-describedby="submit-hint"
      >
        {isUploading ? "素材上传中…" : "提交制作"}
      </button>
      <p id="submit-hint" className="mt-2 text-center text-[11px] text-zinc-600">
        {allReady
          ? "素材已就绪；提交任务与后台处理进度将在下一步接入"
          : `还需上传 ${template.requiredImageCount - readyCount} 张素材`}
      </p>
    </div>
  );
}
