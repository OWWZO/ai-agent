import {
  buildFilePreviewUrlForBrowser,
  normalizeFileUrlForBrowser,
} from "@/utils/fileUrl";

function basenameOf(pathLike: string): string {
  const normalized = pathLike.replace(/\\/g, "/");
  const segments = normalized.split("/").filter(Boolean);
  return segments[segments.length - 1] || pathLike;
}

function isBrowserReachableUrl(value: string): boolean {
  return /^(?:https?:)?\/\//i.test(value) || /^(?:blob:|data:)/i.test(value);
}

function isToolFilePath(value: string): boolean {
  return (
    value.startsWith("/tool/") ||
    value.startsWith("preview/") ||
    value.startsWith("/preview/") ||
    value.startsWith("download/") ||
    value.startsWith("/download/")
  );
}

/**
 * Model3D / media src 来自模型输出：可能是 preview URL、文件名或本地路径。
 * 统一转成当前页面可请求的地址，避免 127.0.0.1:1601 和空格文件名加载失败。
 */
export function resolveGenUiAssetUrl(
  raw?: string | null,
  requestId?: string | null
): string {
  const value = (raw || "").trim();
  if (!value) {
    return "";
  }
  if (/^(?:blob:|data:)/i.test(value)) {
    return value;
  }
  if (isBrowserReachableUrl(value) || isToolFilePath(value)) {
    return normalizeFileUrlForBrowser(value);
  }

  const fileName = basenameOf(value);
  if (requestId && fileName) {
    const preview = buildFilePreviewUrlForBrowser(requestId, fileName);
    if (preview) {
      return preview;
    }
  }
  return normalizeFileUrlForBrowser(value);
}
