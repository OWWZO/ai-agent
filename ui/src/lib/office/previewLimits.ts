/**
 * Office 二进制预览（.doc / .docx）的体积上限与错误分类。
 *
 * 对齐 ZCode `packages/services/src/file/fileService.ts` 的
 * `MAX_BINARY_PREVIEW_BYTES = 25 * 1024 * 1024`：超过这个体积不做在线预览。
 *
 * 本项目在**前端**做这件事（没有 RPC `stat()` 可依赖）：优先用响应头的
 * `Content-Length` 在下载前拒绝，拿不到头再等 `arrayBuffer()` 完成后按真实字节数兜底。
 * 否则大 `.doc` 会把整个 ArrayBuffer 拉进内存，`@extend-ai/react-docx` 解析时还要再复制一份。
 */
export const MAX_OFFICE_PREVIEW_BYTES = 25 * 1024 * 1024;

export const MAX_OFFICE_PREVIEW_MB = Math.round(MAX_OFFICE_PREVIEW_BYTES / 1024 / 1024);

/** 文件超过预览上限。 */
export class OfficeFileTooLargeError extends Error {
  readonly sizeBytes: number;

  constructor(sizeBytes: number) {
    super(`Office 文件超过 ${MAX_OFFICE_PREVIEW_MB}MB，已停止在线预览`);
    this.name = "OfficeFileTooLargeError";
    this.sizeBytes = sizeBytes;
  }
}

/** 文件不存在（HTTP 404）—— 与「网络不可用 / 资源失效」区分开。 */
export class OfficeFileMissingError extends Error {
  constructor() {
    super("文件不存在或已被清理");
    this.name = "OfficeFileMissingError";
  }
}

export function isOfficeFileTooLargeError(error: unknown): error is OfficeFileTooLargeError {
  return error instanceof OfficeFileTooLargeError;
}

export function isOfficeFileMissingError(error: unknown): error is OfficeFileMissingError {
  return error instanceof OfficeFileMissingError;
}

/** 体积预检：`sizeBytes` 为 null / NaN / 0 时视为未知，直接放行。 */
export function assertOfficePreviewSize(sizeBytes: number | null | undefined): void {
  if (
    typeof sizeBytes === "number" &&
    Number.isFinite(sizeBytes) &&
    sizeBytes > MAX_OFFICE_PREVIEW_BYTES
  ) {
    throw new OfficeFileTooLargeError(sizeBytes);
  }
}
