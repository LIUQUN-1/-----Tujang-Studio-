import { formatBytes, mimeLabel } from "./format";
import { apiUpload } from "./api";
import type { AssetDto } from "./types";

/** 上传前需要知道的模板约束（详情接口里就有）。 */
export type UploadConstraints = {
  id: string;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
};

const EXT_TO_MIME: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
  gif: "image/gif",
  heic: "image/heic",
};

/**
 * 判断真实 MIME：优先用浏览器给的 type，缺失时退回扩展名。
 *
 * <p>前端这一步只为**即时反馈**，服务端仍会用文件魔数重新判定——
 * 前端校验永远不是安全边界。
 */
export function detectMime(file: File): string {
  if (file.type && file.type !== "application/octet-stream") {
    return file.type;
  }
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  return EXT_TO_MIME[ext] ?? "application/octet-stream";
}

/**
 * 单张素材的前置校验。通过返回 null，否则返回**可直接展示**的中文错误。
 *
 * @param position 第几张（从 1 开始），用在文案里，方便用户定位是哪一位出了问题
 */
export function validateFile(
  file: File,
  constraints: UploadConstraints,
  position: number,
): string | null {
  if (file.size === 0) {
    return `第 ${position} 张文件为空，请重新选择`;
  }

  const mime = detectMime(file);
  const allowed = constraints.acceptedMimeTypes;
  if (allowed.length > 0 && !allowed.includes(mime)) {
    const allowedLabel = allowed.map(mimeLabel).join(" / ");
    return `第 ${position} 张需为 ${allowedLabel}，当前为 ${mimeLabel(mime)}`;
  }

  if (file.size > constraints.maxFileSizeBytes) {
    return `第 ${position} 张 ${formatBytes(file.size)}，超过上限 ${formatBytes(constraints.maxFileSizeBytes)}`;
  }

  return null;
}

/** 上传单张素材。失败时抛出 ApiError，调用方按 code/message 展示。 */
export function uploadAsset(
  file: File,
  templateId: string,
  signal?: AbortSignal,
): Promise<AssetDto> {
  const form = new FormData();
  form.append("file", file);
  form.append("templateId", templateId);
  return apiUpload<AssetDto>("/api/uploads", form, signal);
}
