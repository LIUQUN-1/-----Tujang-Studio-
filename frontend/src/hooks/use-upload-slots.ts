"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError } from "@/lib/api";
import type { TemplateDetail } from "@/lib/types";
import { uploadAsset, validateFile } from "@/lib/upload";

export type SlotStatus = "empty" | "uploading" | "ready" | "error";

export type UploadSlot = {
  key: number;
  position: number;
  status: SlotStatus;
  /** 上传中为先本地 blob，成功后换成服务端地址 */
  previewUrl?: string;
  assetId?: string;
  fileName?: string;
  error?: string;
};

/**
 * 管理 N 个上传位的状态。
 *
 * <p>每个上传位是**独立**的：各自的 AbortController、各自的 blob URL、各自的重试。
 * 这样某一位失败不会影响其他位，移除某位也不会误删别人的预览。
 * 卸载时统一 abort + revoke，避免请求回来覆盖已卸载组件的状态。
 */
export function useUploadSlots(template: TemplateDetail) {
  const [slots, setSlots] = useState<UploadSlot[]>(() =>
    Array.from({ length: template.requiredImageCount }, (_, i) => ({
      key: i,
      position: i + 1,
      status: "empty" as const,
    })),
  );

  const controllers = useRef(new Map<number, AbortController>());
  const blobUrls = useRef(new Map<number, string>());
  const pickedFiles = useRef(new Map<number, File>());

  const patch = useCallback((key: number, next: Partial<UploadSlot>) => {
    setSlots((prev) => prev.map((slot) => (slot.key === key ? { ...slot, ...next } : slot)));
  }, []);

  const revokeBlob = useCallback((key: number) => {
    const url = blobUrls.current.get(key);
    if (url) {
      URL.revokeObjectURL(url);
      blobUrls.current.delete(key);
    }
  }, []);

  const pick = useCallback(
    async (key: number, file: File) => {
      const position = key + 1;
      const validationError = validateFile(file, template, position);

      // 无论成败，先取消这一位上还没结束的请求
      controllers.current.get(key)?.abort();
      revokeBlob(key);

      if (validationError) {
        pickedFiles.current.set(key, file);
        patch(key, {
          status: "error",
          error: validationError,
          fileName: file.name,
          previewUrl: undefined,
          assetId: undefined,
        });
        return;
      }

      pickedFiles.current.set(key, file);
      const blobUrl = URL.createObjectURL(file);
      blobUrls.current.set(key, blobUrl);
      patch(key, {
        status: "uploading",
        previewUrl: blobUrl,
        fileName: file.name,
        error: undefined,
        assetId: undefined,
      });

      const controller = new AbortController();
      controllers.current.set(key, controller);

      try {
        const asset = await uploadAsset(file, template.id, controller.signal);
        if (controller.signal.aborted) return;
        revokeBlob(key);
        patch(key, {
          status: "ready",
          previewUrl: asset.url,
          assetId: asset.assetId,
          error: undefined,
        });
      } catch (err) {
        if (controller.signal.aborted) return;
        const message =
          err instanceof ApiError ? err.message : "上传失败，请检查网络后重试";
        patch(key, { status: "error", error: message });
      } finally {
        if (controllers.current.get(key) === controller) {
          controllers.current.delete(key);
        }
      }
    },
    [patch, revokeBlob, template],
  );

  const remove = useCallback(
    (key: number) => {
      controllers.current.get(key)?.abort();
      controllers.current.delete(key);
      revokeBlob(key);
      pickedFiles.current.delete(key);
      patch(key, {
        status: "empty",
        previewUrl: undefined,
        assetId: undefined,
        fileName: undefined,
        error: undefined,
      });
    },
    [patch, revokeBlob],
  );

  const retry = useCallback(
    (key: number) => {
      const file = pickedFiles.current.get(key);
      if (file) {
        void pick(key, file);
      }
    },
    [pick],
  );

  // 卸载时收尾：中断请求 + 释放本地预览
  useEffect(() => {
    const activeControllers = controllers.current;
    const activeUrls = blobUrls.current;
    return () => {
      activeControllers.forEach((controller) => controller.abort());
      activeControllers.clear();
      activeUrls.forEach((url) => URL.revokeObjectURL(url));
      activeUrls.clear();
    };
  }, []);

  const readyCount = slots.filter((slot) => slot.status === "ready").length;
  const isUploading = slots.some((slot) => slot.status === "uploading");

  return {
    slots,
    readyCount,
    isUploading,
    allReady: readyCount === slots.length,
    pick,
    remove,
    retry,
  };
}
