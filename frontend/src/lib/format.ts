/** 展示层的格式化工具。纯函数，方便单测。 */

const MIME_LABELS: Record<string, string> = {
  "image/jpeg": "JPG",
  "image/png": "PNG",
  "image/webp": "WebP",
};

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  const mb = bytes / (1024 * 1024);
  return `${Number.isInteger(mb) ? mb : mb.toFixed(1)} MB`;
}

/** ["image/jpeg","image/png"] → "JPG / PNG"，未知类型原样展示。 */
export function formatMimeTypes(mimes: string[]): string {
  if (!mimes.length) return "不限";
  return mimes.map(mimeLabel).join(" / ");
}

/** 单个 MIME 的短标签，未知类型原样返回。 */
export function mimeLabel(mime: string): string {
  return MIME_LABELS[mime] ?? mime;
}
