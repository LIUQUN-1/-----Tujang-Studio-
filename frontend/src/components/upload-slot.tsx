"use client";

import { useId, useState } from "react";

import type { UploadSlot as Slot } from "@/hooks/use-upload-slots";

type Props = {
  slot: Slot;
  accept: string;
  onPick: (key: number, file: File) => void;
  onRemove: (key: number) => void;
  onRetry: (key: number) => void;
};

/**
 * 单个上传位：空态 / 上传中 / 就绪 / 失败 四种形态。
 *
 * <p>可访问性：文件输入用 sr-only 隐藏而不是 display:none，这样 Tab 仍能聚焦、
 * 回车能打开选择框；替换与移除是带 aria-label 的真按钮。
 *
 * <p>失败分两种：校验没过（没有预览可看）和上传失败（有本地预览）。
 * 前者给"重新选择"，后者给"重试"，避免让用户重新挑一遍文件。
 */
export function UploadSlot({ slot, accept, onPick, onRemove, onRetry }: Props) {
  const inputId = useId();
  const [dragging, setDragging] = useState(false);

  const label = `第 ${slot.position} 张素材`;

  const handleFiles = (files: FileList | null) => {
    const file = files?.[0];
    if (file) {
      onPick(slot.key, file);
    }
  };

  const borderClass = dragging
    ? "border-accent bg-accent/10"
    : slot.status === "error"
      ? "border-red-400/50 bg-red-500/[0.04]"
      : "border-white/10 bg-white/[0.02]";

  return (
    <div
      className={`relative overflow-hidden rounded-xl border transition-colors ${borderClass}`}
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        handleFiles(event.dataTransfer.files);
      }}
    >
      <input
        id={inputId}
        type="file"
        accept={accept}
        className="sr-only"
        aria-label={`上传${label}`}
        onChange={(event) => {
          handleFiles(event.target.files);
          // 允许重复选择同一个文件时仍然触发 change
          event.target.value = "";
        }}
      />

      {slot.status === "empty" && (
        <label
          htmlFor={inputId}
          className="flex aspect-[4/3] cursor-pointer flex-col items-center justify-center gap-2 px-3 text-center"
        >
          <span
            aria-hidden
            className="grid h-9 w-9 place-items-center rounded-full bg-white/[0.06] text-lg text-zinc-400"
          >
            ＋
          </span>
          <span className="text-xs text-zinc-400">点击或拖入图片</span>
          <span className="text-[11px] text-zinc-600">{label}</span>
        </label>
      )}

      {/* 校验没过：没有预览可展示，直接给原因和重新选择 */}
      {slot.status === "error" && !slot.previewUrl && (
        <div className="flex aspect-[4/3] flex-col items-center justify-center gap-2 px-3 text-center">
          <span
            aria-hidden
            className="grid h-8 w-8 place-items-center rounded-full bg-red-500/15 text-sm text-red-300"
          >
            !
          </span>
          <p role="alert" className="text-[11px] leading-relaxed text-red-300">
            {slot.error}
          </p>
          <div className="flex gap-1.5">
            <label
              htmlFor={inputId}
              className="cursor-pointer rounded-lg bg-white/10 px-2.5 py-1 text-[11px] text-zinc-100 hover:bg-white/20"
              aria-label={`重新选择${label}`}
            >
              重新选择
            </label>
            <button
              type="button"
              onClick={() => onRemove(slot.key)}
              className="rounded-lg bg-white/5 px-2.5 py-1 text-[11px] text-zinc-400 hover:bg-white/10"
              aria-label={`移除${label}`}
            >
              移除
            </button>
          </div>
        </div>
      )}

      {slot.previewUrl && (
        <div className="relative aspect-[4/3]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={slot.previewUrl}
            alt={`${label}预览`}
            className={`h-full w-full object-cover ${slot.status === "uploading" ? "opacity-50" : ""}`}
          />

          {slot.status === "uploading" && (
            <div className="absolute inset-0 grid place-items-center bg-black/40">
              <span className="flex items-center gap-2 rounded-full bg-black/70 px-3 py-1.5 text-xs text-zinc-200">
                <span
                  aria-hidden
                  className="h-3 w-3 animate-spin rounded-full border-2 border-white/30 border-t-white"
                />
                上传中…
              </span>
            </div>
          )}

          {slot.status === "ready" && (
            <div className="absolute inset-x-0 bottom-0 flex items-center justify-between gap-2 bg-gradient-to-t from-black/85 to-transparent px-2 pb-2 pt-6">
              <span className="truncate text-[11px] text-zinc-300">{slot.fileName}</span>
              <div className="flex shrink-0 gap-1">
                <label
                  htmlFor={inputId}
                  className="cursor-pointer rounded-lg bg-white/10 px-2 py-1 text-[11px] text-zinc-100 hover:bg-white/20"
                  aria-label={`替换${label}`}
                >
                  替换
                </label>
                <button
                  type="button"
                  onClick={() => onRemove(slot.key)}
                  className="rounded-lg bg-white/10 px-2 py-1 text-[11px] text-zinc-100 hover:bg-white/20"
                  aria-label={`移除${label}`}
                >
                  移除
                </button>
              </div>
            </div>
          )}

          {slot.status === "error" && (
            <div className="absolute inset-x-0 bottom-0 flex items-center justify-between gap-2 bg-gradient-to-t from-black/90 to-transparent px-2 pb-2 pt-6">
              <span className="truncate text-[11px] text-zinc-300">{slot.fileName}</span>
              <div className="flex shrink-0 gap-1">
                <button
                  type="button"
                  onClick={() => onRetry(slot.key)}
                  className="rounded-lg bg-accent px-2 py-1 text-[11px] font-medium text-ink-950 hover:bg-accent-soft"
                  aria-label={`重试上传${label}`}
                >
                  重试
                </button>
                <button
                  type="button"
                  onClick={() => onRemove(slot.key)}
                  className="rounded-lg bg-white/10 px-2 py-1 text-[11px] text-zinc-100 hover:bg-white/20"
                  aria-label={`移除${label}`}
                >
                  移除
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {slot.status === "error" && slot.previewUrl && slot.error && (
        <p
          role="alert"
          className="border-t border-red-400/20 px-2.5 py-2 text-[11px] leading-relaxed text-red-300"
        >
          {slot.error}
        </p>
      )}
    </div>
  );
}
