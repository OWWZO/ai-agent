import type {
  DecodeResult,
  ExtractedImageHit,
  ToolFileInfo,
} from "./types";
import {
  normalizeFileUrlForBrowser,
  normalizeToolBaseUrlForBrowser,
} from "@/utils/fileUrl";

const DATA_URL_RE = /^data:(image\/[a-z0-9.+-]+);base64,(.+)$/i;
const MARKDOWN_IMAGE_RE = /!\[[^\]]*]\((https?:\/\/[^)\s]+)\)/i;
const HTTP_IMAGE_RE = /https?:\/\/[^\s"'<>)]*\.(?:png|jpe?g|gif|webp|bmp|svg)(?:\?[^\s"'<>)]*)?/i;
const BASE64_IMAGE_RE = /[A-Za-z0-9+/=\s]{200,}/;

const MIME_EXTENSION_MAP: Record<string, string> = {
  "image/png": "png",
  "image/jpeg": "jpg",
  "image/gif": "gif",
  "image/webp": "webp",
  "image/bmp": "bmp",
  "image/svg+xml": "svg",
};

export const IMAGE_GENERATION_STORAGE_KEY = "workspace-image-generation:config";

export const checkerboardStyle = {
  backgroundImage:
    "linear-gradient(45deg, #f1f5f9 25%, transparent 25%), linear-gradient(-45deg, #f1f5f9 25%, transparent 25%), linear-gradient(45deg, transparent 75%, #f1f5f9 75%), linear-gradient(-45deg, transparent 75%, #f1f5f9 75%)",
  backgroundSize: "16px 16px",
  backgroundPosition: "0 0, 0 8px, 8px -8px, -8px 0",
  backgroundColor: "#ffffff",
} as const;

export function buildDefaultToolBaseUrl(): string {
  return normalizeToolBaseUrlForBrowser(REACTOR_TOOL_BASE_URL);
}

export function trimTrailingSlash(url: string): string {
  return (url || "").trim().replace(/\/+$/, "");
}

export function createLocalId(prefix: string): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function normalizeToDataUrl(rawText: string): DecodeResult {
  const raw = rawText.trim();
  if (!raw) {
    throw new Error("请输入 Base64 编码或 Data URL");
  }

  let mimeType = "image/png";
  let base64 = raw;
  const dataUrlMatch = raw.match(DATA_URL_RE);
  if (dataUrlMatch) {
    mimeType = dataUrlMatch[1].toLowerCase();
    base64 = dataUrlMatch[2];
  }

  base64 = base64.replace(/\s+/g, "");
  if (!/^[A-Za-z0-9+/=]+$/.test(base64)) {
    throw new Error("当前内容不是合法的 Base64 图片数据");
  }

  const fileExtension = MIME_EXTENSION_MAP[mimeType] || "png";
  return {
    dataUrl: `data:${mimeType};base64,${base64}`,
    mimeType,
    fileExtension,
    byteLength: estimateBase64Bytes(base64),
    base64Length: base64.length,
  };
}

export function estimateBase64Bytes(base64: string): number {
  const padding = (base64.match(/=+$/)?.[0].length || 0);
  return Math.max(0, Math.floor((base64.length * 3) / 4) - padding);
}

export function formatBytes(size?: number | null): string {
  const value = size || 0;
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(2)} MB`;
}

export function extractImageFromUnknown(payload: unknown): ExtractedImageHit | null {
  if (payload && typeof payload === "object") {
    const record = payload as Record<string, unknown>;

    if (Array.isArray(record.output)) {
      for (const item of record.output) {
        if (!item || typeof item !== "object") {
          continue;
        }
        const outputItem = item as Record<string, unknown>;
        if (outputItem.type === "image_generation_call" && typeof outputItem.result === "string") {
          try {
            return { dataUrl: normalizeToDataUrl(outputItem.result).dataUrl };
          } catch {
            // 忽略非法片段，继续向下兼容。
          }
        }
        if (outputItem.type === "message" && Array.isArray(outputItem.content)) {
          for (const part of outputItem.content) {
            if (!part || typeof part !== "object") {
              continue;
            }
            const contentPart = part as Record<string, unknown>;
            if (contentPart.type === "output_image") {
              const imageValue =
                contentPart.image_url || contentPart.url || contentPart.b64_json || contentPart.image;
              if (typeof imageValue === "string") {
                const found = findImageInText(imageValue);
                if (found) {
                  return found;
                }
              }
            }
          }
        }
      }
    }

    if (Array.isArray(record.data)) {
      for (const item of record.data) {
        if (!item || typeof item !== "object") {
          continue;
        }
        const dataItem = item as Record<string, unknown>;
        if (typeof dataItem.url === "string" && dataItem.url) {
          return { url: dataItem.url };
        }
        if (typeof dataItem.b64_json === "string" && dataItem.b64_json) {
          try {
            return { dataUrl: normalizeToDataUrl(dataItem.b64_json).dataUrl };
          } catch {
            // 忽略非法片段，继续向下兼容。
          }
        }
      }
    }

    if (Array.isArray(record.choices)) {
      for (const choice of record.choices) {
        if (!choice || typeof choice !== "object") {
          continue;
        }
        const message = (choice as Record<string, unknown>).message || (choice as Record<string, unknown>).delta;
        if (message && typeof message === "object") {
          const messageContent = (message as Record<string, unknown>).content;
          if (typeof messageContent === "string") {
            const found = findImageInText(messageContent);
            if (found) {
              return found;
            }
          }
          if (Array.isArray(messageContent)) {
            for (const part of messageContent) {
              if (!part || typeof part !== "object") {
                continue;
              }
              const text = (part as Record<string, unknown>).text;
              if (typeof text === "string") {
                const found = findImageInText(text);
                if (found) {
                  return found;
                }
              }
            }
          }
        }
      }
    }
  }

  if (typeof payload === "string") {
    return findImageInText(payload);
  }

  return findImageInText(JSON.stringify(payload ?? "", null, 2));
}

export function extractTextFromUnknown(payload: unknown): string {
  if (typeof payload === "string") {
    return payload;
  }
  if (!payload || typeof payload !== "object") {
    return "";
  }

  const record = payload as Record<string, unknown>;
  const textParts: string[] = [];

  if (typeof record.output_text === "string" && record.output_text.trim()) {
    textParts.push(record.output_text.trim());
  }

  if (Array.isArray(record.output)) {
    for (const item of record.output) {
      if (!item || typeof item !== "object") {
        continue;
      }
      const outputItem = item as Record<string, unknown>;
      if (!Array.isArray(outputItem.content)) {
        continue;
      }
      for (const part of outputItem.content) {
        if (!part || typeof part !== "object") {
          continue;
        }
        const text = (part as Record<string, unknown>).text;
        if (typeof text === "string" && text.trim()) {
          textParts.push(text.trim());
        }
      }
    }
  }

  if (Array.isArray(record.choices)) {
    for (const choice of record.choices) {
      if (!choice || typeof choice !== "object") {
        continue;
      }
      const message = (choice as Record<string, unknown>).message || (choice as Record<string, unknown>).delta;
      if (message && typeof message === "object") {
        const content = (message as Record<string, unknown>).content;
        if (typeof content === "string" && content.trim()) {
          textParts.push(content.trim());
        }
      }
    }
  }

  return textParts.join("\n").trim();
}

export function findImageInText(text: string): ExtractedImageHit | null {
  if (!text) {
    return null;
  }

  const dataUrlMatch = text.match(/data:image\/[a-z0-9.+-]+;base64,[A-Za-z0-9+/=\s]+/i);
  if (dataUrlMatch) {
    try {
      return { dataUrl: normalizeToDataUrl(dataUrlMatch[0]).dataUrl };
    } catch {
      // 忽略非法片段。
    }
  }

  const markdownMatch = text.match(MARKDOWN_IMAGE_RE);
  if (markdownMatch) {
    return { url: markdownMatch[1] };
  }

  const urlMatch = text.match(HTTP_IMAGE_RE);
  if (urlMatch) {
    return { url: urlMatch[0] };
  }

  const base64Match = text.match(BASE64_IMAGE_RE);
  if (base64Match) {
    try {
      return { dataUrl: normalizeToDataUrl(base64Match[0]).dataUrl };
    } catch {
      // 忽略非法片段。
    }
  }

  return null;
}

export function downloadDataUrl(dataUrl: string, fileName: string) {
  const anchor = document.createElement("a");
  anchor.href = dataUrl;
  anchor.download = fileName;
  anchor.rel = "noopener";
  anchor.click();
}

export function toPrettyJson(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export async function fileToDataUrl(file: File): Promise<string> {
  return await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error || new Error("读取图片失败"));
    reader.readAsDataURL(file);
  });
}

export async function loadImageElement(src: string): Promise<HTMLImageElement> {
  return await new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("图片加载失败"));
    image.src = src;
  });
}

export async function resolveImageNaturalSize(src: string): Promise<{ width: number; height: number }> {
  const image = await loadImageElement(src);
  return {
    width: image.naturalWidth,
    height: image.naturalHeight,
  };
}

export function hasCanvasDrawing(canvas: HTMLCanvasElement): boolean {
  const context = canvas.getContext("2d");
  if (!context) {
    return false;
  }
  const { data } = context.getImageData(0, 0, canvas.width, canvas.height);
  for (let index = 3; index < data.length; index += 4) {
    if (data[index] > 0) {
      return true;
    }
  }
  return false;
}

export async function buildMaskedComposite(options: {
  imageSrc: string;
  maskDataUrl: string;
  width: number;
  height: number;
}): Promise<string> {
  const image = await loadImageElement(options.imageSrc);
  const mask = await loadImageElement(options.maskDataUrl);
  const canvas = document.createElement("canvas");
  canvas.width = options.width || image.naturalWidth;
  canvas.height = options.height || image.naturalHeight;

  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("无法创建蒙版画布");
  }

  context.drawImage(image, 0, 0, canvas.width, canvas.height);
  context.drawImage(mask, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL("image/png");
}

export function resolvePreviewUrl(fileInfo: ToolFileInfo): string {
  return normalizeFileUrlForBrowser(
    fileInfo.previewUrl ||
    fileInfo.domainUrl ||
    fileInfo.downloadUrl ||
    fileInfo.ossUrl ||
    ""
  );
}

export function resolveDownloadUrl(fileInfo: ToolFileInfo): string {
  return normalizeFileUrlForBrowser(
    fileInfo.downloadUrl ||
    fileInfo.ossUrl ||
    fileInfo.previewUrl ||
    fileInfo.domainUrl ||
    ""
  );
}

export function formatHistoryTime(rawTime?: string | null): string {
  if (!rawTime) {
    return "未知时间";
  }

  const date = new Date(rawTime);
  if (Number.isNaN(date.getTime())) {
    return rawTime;
  }

  return date.toLocaleString("zh-CN", {
    hour12: false,
  });
}
