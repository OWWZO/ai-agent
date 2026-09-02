import {
  buildFilePreviewUrlForBrowser,
  normalizeFileUrlForBrowser,
} from "@/utils/fileUrl";
import { resolveTaskResultMap } from "@/utils/chat/toolCalls";

/**
 * 任务产物和附件引用归一化工具。
 *
 * <p>实时事件、历史回放和工具结果的文件字段并不完全一致；本模块将它们收敛为
 * {@code CHAT.TFile}，并集中决定预览、下载、复制和缺失资源语义，页面组件不直接
 * 解析后端的多种旧字段。</p>
 */
const toText = (value: unknown) => {
  if (value == null) {
    return "";
  }
  return String(value).trim();
};

const firstText = (...values: unknown[]) => {
  for (const value of values) {
    const text = toText(value);
    if (text) {
      return text;
    }
  }
  return "";
};

const isBareFileReference = (value: string) => {
  return Boolean(value) &&
    !value.includes("/") &&
    !value.includes("\\") &&
    !/^[a-z][a-z0-9+.-]*:/i.test(value) &&
    !value.startsWith("//") &&
    !value.startsWith("#");
};

export function basenameOfPath(pathLike?: string | null): string {
  const normalized = toText(pathLike).replace(/\\/g, "/");
  if (!normalized) {
    return "";
  }
  const segments = normalized.split("/").filter(Boolean);
  return segments[segments.length - 1] || normalized;
}

export function normalizeRelativePath(pathLike?: string | null): string {
  let stripped = toText(pathLike).replace(/\\/g, "/");
  if (!stripped) {
    return "";
  }
  while (stripped.startsWith("./")) {
    stripped = stripped.slice(2);
  }
  while (stripped.startsWith("/")) {
    stripped = stripped.slice(1);
  }
  const parts = stripped.split("/").filter((part) => part && part !== ".");
  if (!parts.length || parts.some((part) => part === "..")) {
    return "";
  }
  return parts.join("/");
}

/** workspace_write/edit 的 file 事件只同步文件目录，不参与工作区自动跟随。 */
export function isFileListOnlyTask(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task> | null
): boolean {
  if (!task) {
    return false;
  }
  const resultMap = resolveTaskResultMap(task);
  const taskType = toText(task.messageType).toLowerCase();
  const resultType = toText(resultMap.messageType).toLowerCase();
  const messageType = taskType && taskType !== "task" ? taskType : resultType;
  const value = resultMap.fileListOnly ??
    (task as unknown as Record<string, unknown>).fileListOnly;
  return messageType === "file" && (
    value === true || String(value).toLowerCase() === "true"
  );
}

const parseWorkspaceDescriptionPath = (description?: unknown) => {
  const text = toText(description);
  if (/^workspace:/i.test(text)) {
    return text.slice("workspace:".length);
  }
  return "";
};

const resolveTaskFileUrl = (
  rawUrl: unknown,
  fileName: string,
  requestId?: string
) => {
  const value = firstText(rawUrl);
  if (isBareFileReference(value)) {
    const fallback = buildFilePreviewUrlForBrowser(requestId, fileName);
    return fallback || normalizeFileUrlForBrowser(value);
  }
  return normalizeFileUrlForBrowser(value);
};

const toSize = (value: unknown) => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const toExtension = (name: string, fallbackType?: string) => {
  const base = String(name || "").trim();
  const fromName = base.includes(".")
    ? base.split(".").pop()?.toLowerCase() || ""
    : "";
  // 避免把「无扩展名的中文名」整段当成 type；也归一化 mime 如 text/html
  if (fromName && fromName.length <= 8 && !fromName.includes("/") && fromName !== base.toLowerCase()) {
    return fromName;
  }
  const fallback = String(fallbackType || "").toLowerCase().trim();
  if (!fallback) {
    return fromName;
  }
  if (fallback.includes("html")) return "html";
  if (fallback.includes("pdf")) return "pdf";
  if (fallback.includes("markdown")) return "md";
  if (fallback.includes("json")) return "json";
  if (fallback.includes("csv") || fallback.includes("excel") || fallback.includes("spreadsheet")) {
    return fallback.includes("csv") ? "csv" : "xlsx";
  }
  if (fallback.includes("png") || fallback.includes("jpeg") || fallback.includes("jpg") || fallback.includes("image/")) {
    if (fallback.includes("png")) return "png";
    if (fallback.includes("jpeg") || fallback.includes("jpg")) return "jpg";
    return "png";
  }
  // text/plain → txt；application/xxx → 取末段
  if (fallback.includes("/")) {
    const leaf = fallback.split("/").pop() || "";
    if (leaf === "plain") return "txt";
    if (leaf.length <= 8) return leaf;
  }
  return fallback.length <= 8 ? fallback : fromName;
};

const IMAGE_FILE_EXTENSIONS = new Set([
  "png",
  "jpg",
  "jpeg",
  "gif",
  "webp",
  "bmp",
  "svg",
  "svg+xml",
  "avif",
  "ico",
]);

const PDF_FILE_EXTENSIONS = new Set(["pdf"]);

/** 可客户端转 HTML 预览的 Word（OOXML） */
const DOCX_FILE_EXTENSIONS = new Set(["docx"]);

/** 老式 Word，浏览器端不做预览 */
const LEGACY_DOC_EXTENSIONS = new Set(["doc"]);

const EXCEL_FILE_EXTENSIONS = new Set(["csv", "xlsx", "xls"]);

const TEXT_COPYABLE_EXTENSIONS = new Set([
  "md",
  "markdown",
  "txt",
  "json",
  "js",
  "ts",
  "tsx",
  "jsx",
  "py",
  "java",
  "xml",
  "html",
  "htm",
  "css",
  "yml",
  "yaml",
  "sql",
  "sh",
  "log",
  "csv",
]);

const normalizeExtension = (value?: string | null) => {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/^\./, "");
};

const resolveFileExtension = (
  fileLike?: Pick<CHAT.TFile, "type" | "name"> | null
) => {
  if (!fileLike) {
    return "";
  }
  const fromType = normalizeExtension(fileLike.type);
  if (fromType) {
    return fromType;
  }
  return normalizeExtension(fileLike.name?.split(".").pop());
};

/**
 * 不同来源的任务附件可能只带 fileInfo，也可能只带 artifactRefs。
 * 这里统一归一化成前端稳定的文件结构，避免各组件各写一套兜底逻辑。
 */
export const normalizeTaskFile = (
  raw: any,
  options?: { requestId?: string; sessionId?: string }
): CHAT.TFile | null => {
  if (!raw || typeof raw !== "object") {
    return null;
  }

  const resourceKey = firstText(
    raw.resourceKey,
    raw.ossUrl,
    raw.downloadUrl,
    raw.domainUrl,
    raw.fileName,
    raw.displayName,
    raw.name
  );
  const relativePath = normalizeRelativePath(
    firstText(
      raw.relativePath,
      toText(raw.originFileName).includes("/") || toText(raw.originFileName).includes("\\")
        ? raw.originFileName
        : undefined,
      parseWorkspaceDescriptionPath(raw.description),
      toText(raw.fileName).includes("/") || toText(raw.fileName).includes("\\")
        ? raw.fileName
        : undefined
    )
  );
  const name = firstText(
    relativePath ? basenameOfPath(relativePath) : undefined,
    raw.displayName,
    raw.fileName,
    raw.name,
    isBareFileReference(toText(raw.url)) ? raw.url : undefined,
    resourceKey,
    "未命名文件"
  );
  const requestId = firstText(
    options?.sessionId,
    options?.requestId,
    raw.sessionId,
    raw.requestId
  );
  const urlName = relativePath || name;
  const previewUrl = resolveTaskFileUrl(
    firstText(raw.previewUrl, raw.domainUrl, raw.url, raw.ossUrl, raw.downloadUrl),
    urlName,
    requestId
  );
  const downloadUrl = resolveTaskFileUrl(
    firstText(raw.downloadUrl, raw.ossUrl, raw.domainUrl, raw.url, raw.previewUrl),
    urlName,
    requestId
  );
  const missing = Boolean(raw.missing) || (!previewUrl && !downloadUrl);

  return {
    name,
    url: previewUrl || downloadUrl || "",
    type: toExtension(name, raw.artifactType || raw.type),
    size: toSize(raw.fileSize ?? raw.size),
    downloadUrl: downloadUrl || undefined,
    missing,
    missingReason: firstText(
      raw.missingReason,
      missing ? "引用资源不存在或已失效" : undefined
    ) || undefined,
    resourceKey: resourceKey || undefined,
    mimeType: raw.mimeType ?? null,
    originFileName: firstText(raw.originFileName, relativePath) || undefined,
    relativePath: relativePath || undefined,
  };
};

export const artifactRefsToFileInfo = (artifactRefs?: unknown[]) => {
  if (!Array.isArray(artifactRefs) || !artifactRefs.length) {
    return [];
  }

  return artifactRefs
    .map((artifact) => normalizeTaskFile(artifact))
    .filter((file): file is CHAT.TFile => Boolean(file))
    .map((file) => ({
      fileName: file.name,
      ossUrl: file.downloadUrl || file.url,
      fileSize: file.size,
      domainUrl: file.url,
      downloadUrl: file.downloadUrl,
      missing: file.missing,
      missingReason: file.missingReason,
      resourceKey: file.resourceKey,
      relativePath: file.relativePath,
      originFileName: file.originFileName,
    }));
};

/**
 * 图片文件既可能带 mimeType，也可能只能从扩展名识别。
 * 统一收口后，附件列表和工作区预览就不会各自维护一套判断规则。
 */
export const isImageFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike) {
    return false;
  }

  if (fileLike.mimeType?.toLowerCase().startsWith("image/")) {
    return true;
  }

  const normalizedType = normalizeExtension(fileLike.type);
  if (IMAGE_FILE_EXTENSIONS.has(normalizedType)) {
    return true;
  }

  const normalizedNameExtension = normalizeExtension(fileLike.name.split(".").pop());
  return IMAGE_FILE_EXTENSIONS.has(normalizedNameExtension);
};

/**
 * PDF：工作区走 pdf.js 预览，不要进文本 FileRenderer。
 */
export const isPdfFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike) {
    return false;
  }
  const mime = fileLike.mimeType?.toLowerCase() || "";
  if (mime === "application/pdf" || mime.includes("application/pdf")) {
    return true;
  }
  return PDF_FILE_EXTENSIONS.has(resolveFileExtension(fileLike));
};

/**
  * DOCX：docx-preview 高保真只读预览。
 */
export const isDocxFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike) {
    return false;
  }
  const mime = fileLike.mimeType?.toLowerCase() || "";
  if (
    mime.includes("officedocument.wordprocessingml") ||
    mime.includes("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  ) {
    return true;
  }
  return DOCX_FILE_EXTENSIONS.has(resolveFileExtension(fileLike));
};

/**
 * 老 .doc：仅下载，不尝试前端解析。
 */
export const isLegacyDocFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike) {
    return false;
  }
  if (isDocxFileLike(fileLike)) {
    return false;
  }
  const mime = fileLike.mimeType?.toLowerCase() || "";
  if (mime === "application/msword") {
    return true;
  }
  return LEGACY_DOC_EXTENSIONS.has(resolveFileExtension(fileLike));
};

/** Word 家族（docx 可预览 + doc 仅下载） */
export const isWordFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => isDocxFileLike(fileLike) || isLegacyDocFileLike(fileLike);

export const isExcelFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike) {
    return false;
  }
  const mime = fileLike.mimeType?.toLowerCase() || "";
  if (
    mime.includes("spreadsheet") ||
    mime.includes("excel") ||
    mime === "text/csv"
  ) {
    return true;
  }
  const ext = resolveFileExtension(fileLike);
  if (EXCEL_FILE_EXTENSIONS.has(ext)) {
    return true;
  }
  const name = String(fileLike.name || "").toLowerCase();
  return name.includes(".csv") || name.includes(".xlsx") || name.includes(".xls");
};

const PPT_FILE_EXTENSIONS = new Set(["ppt", "pptx", "pps", "ppsx"]);
const ARCHIVE_MEDIA_EXTENSIONS = new Set([
  "zip",
  "rar",
  "7z",
  "tar",
  "gz",
  "mp3",
  "mp4",
  "mov",
  "avi",
  "mkv",
  "webm",
  "wav",
  "exe",
  "dmg",
  "apk",
  "wasm",
  "bin",
]);

export const isPptFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike) return false;
  const mime = fileLike.mimeType?.toLowerCase() || "";
  if (mime.includes("presentation") || mime.includes("powerpoint")) return true;
  return PPT_FILE_EXTENSIONS.has(resolveFileExtension(fileLike));
};

/**
 * 二进制/office 等禁止 response.text() 复制。
 */
export const isBinaryPreviewFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike) {
    return false;
  }
  if (
    isImageFileLike(fileLike) ||
    isPdfFileLike(fileLike) ||
    isWordFileLike(fileLike) ||
    isExcelFileLike(fileLike) ||
    isPptFileLike(fileLike)
  ) {
    return true;
  }
  return ARCHIVE_MEDIA_EXTENSIONS.has(resolveFileExtension(fileLike));
};

export const isTextCopyableFileLike = (
  fileLike?: Pick<CHAT.TFile, "type" | "name" | "mimeType"> | null
) => {
  if (!fileLike || isBinaryPreviewFileLike(fileLike)) {
    return false;
  }
  const mime = fileLike.mimeType?.toLowerCase() || "";
  if (mime.startsWith("text/") || mime.includes("json") || mime.includes("xml")) {
    return true;
  }
  return TEXT_COPYABLE_EXTENSIONS.has(resolveFileExtension(fileLike));
};

const readNestedResultMap = (taskLike: any) => {
  const nested = taskLike?.resultMap?.resultMap;
  return nested && typeof nested === "object" ? nested : undefined;
};

const readResultMapFile = (
  resultMap?: Record<string, unknown>,
  requestId?: string
) => {
  if (!resultMap || typeof resultMap !== "object") {
    return null;
  }

  const primaryFileName = firstText(
    resultMap.primaryFileName,
    resultMap.fileName,
    resultMap.filename,
    resultMap.displayName,
    resultMap.name
  );
  const previewUrl = resolveTaskFileUrl(
    firstText(resultMap.previewUrl, resultMap.domainUrl, resultMap.url),
    primaryFileName,
    requestId
  );
  const downloadUrl = resolveTaskFileUrl(
    firstText(
      resultMap.downloadUrl,
      resultMap.ossUrl,
      resultMap.previewUrl,
      resultMap.domainUrl,
      resultMap.url
    ),
    primaryFileName,
    requestId
  );

  if (!previewUrl && !downloadUrl && !primaryFileName) {
    return null;
  }

  return {
    previewUrl,
    downloadUrl,
    domainUrl: previewUrl,
    ossUrl: downloadUrl,
    fileName: primaryFileName,
    displayName: primaryFileName,
  };
};

const readPrimaryResultMapFile = (taskLike: any, requestId?: string) => {
  const resolvedResultMap = resolveTaskResultMap(taskLike);
  const nestedResultMap = readNestedResultMap(taskLike);
  const nestedFile = readResultMapFile(nestedResultMap, requestId);
  const currentFile = readResultMapFile(
    resolvedResultMap as unknown as Record<string, unknown>,
    requestId
  );

  if (!nestedFile && !currentFile) {
    return null;
  }

  return {
    ...(nestedFile || {}),
    ...(currentFile || {}),
  };
};

const readRawFiles = (taskLike: any) => {
  if (!taskLike || typeof taskLike !== "object") {
    return [];
  }

  const nestedResultMap = readNestedResultMap(taskLike);
  const resolvedResultMap = resolveTaskResultMap(taskLike);
  const candidates: unknown[] = [
    taskLike?.artifactRefs,
    taskLike?.fileInfo,
    taskLike?.fileList,
    taskLike?.resultMap?.artifactRefs,
    nestedResultMap?.artifactRefs,
    taskLike?.resultMap?.fileInfo,
    taskLike?.resultMap?.fileList,
    nestedResultMap?.fileInfo,
    nestedResultMap?.fileList,
    resolvedResultMap.artifactRefs,
    resolvedResultMap.fileInfo,
    resolvedResultMap.fileList,
  ];

  const files = candidates.flatMap((candidate) =>
    Array.isArray(candidate) ? candidate : []
  );
  return [...files, ...readToolResultFiles(taskLike)];
};

const readToolResultFiles = (taskLike: any): unknown[] => {
  const resultMap = resolveTaskResultMap(taskLike) as unknown as Record<string, unknown>;
  const candidates = [taskLike?.toolResult, resultMap.toolResult];
  const files: unknown[] = [];

  for (const candidate of candidates) {
    const values = Array.isArray(candidate) ? candidate : [candidate];
    for (const value of values) {
      if (!value) {
        continue;
      }
      if (typeof value === "object") {
        const record = value as Record<string, unknown>;
        files.push(record.fileList, record.fileInfo, record.artifactRefs);
        if (typeof record.toolResult === "string") {
          try {
            files.push(JSON.parse(record.toolResult));
          } catch {
            // 工具结果可能是普通文本，不包含结构化文件信息。
          }
        }
        continue;
      }
      if (typeof value !== "string") {
        continue;
      }
      try {
        const parsed = JSON.parse(value) as Record<string, unknown>;
        files.push(parsed.fileList, parsed.fileInfo, parsed.artifactRefs);
        if (parsed.detail && typeof parsed.detail === "object") {
          const detail = parsed.detail as Record<string, unknown>;
          files.push(detail.fileList, detail.fileInfo, detail.artifactRefs);
        }
      } catch {
        // 工具结果可能是 key=value 文本，不包含结构化文件信息。
      }
    }
  }

  return files.flatMap((value) => (Array.isArray(value) ? value : []));
};

const resolveTaskFileRequestId = (taskLike: any) => {
  if (!taskLike || typeof taskLike !== "object") {
    return "";
  }
  const nestedResultMap = readNestedResultMap(taskLike);
  const resolvedResultMap = resolveTaskResultMap(taskLike);
  return firstText(
    taskLike.sessionId,
    nestedResultMap?.sessionId,
    resolvedResultMap.sessionId,
    taskLike.requestId,
    nestedResultMap?.requestId,
    resolvedResultMap.requestId
  );
};

export const getTaskFiles = (taskLike: any): CHAT.TFile[] => {
  const dedup = new Map<string, CHAT.TFile>();
  const requestId = resolveTaskFileRequestId(taskLike);

  readRawFiles(taskLike).forEach((raw) => {
    const file = normalizeTaskFile(raw, { requestId });
    if (!file) {
      return;
    }
    const key = file.downloadUrl || file.url || file.relativePath || file.resourceKey || file.name;
    if (!key) {
      return;
    }
    const existing = dedup.get(key);
    if (!existing) {
      dedup.set(key, file);
      return;
    }
    dedup.set(key, {
      ...existing,
      ...file,
      relativePath: file.relativePath || existing.relativePath,
      originFileName: file.originFileName || existing.originFileName,
    });
  });

  const files = Array.from(dedup.values());
  const primaryFilePatch = normalizeTaskFile(
    readPrimaryResultMapFile(taskLike, requestId),
    { requestId }
  );

  if (!primaryFilePatch) {
    return files;
  }

  if (!files.length) {
    return [primaryFilePatch];
  }

  // 历史回放里 fileInfo 常带预览地址，而顶层 resultMap 更适合补充主文件名和下载地址。
  files[0] = {
    ...files[0],
    name:
      files[0].name && files[0].name !== "未命名文件"
        ? files[0].name
        : primaryFilePatch.name,
    url: files[0].url || primaryFilePatch.url,
    downloadUrl: primaryFilePatch.downloadUrl || files[0].downloadUrl,
    missing: files[0].missing && primaryFilePatch.missing,
    missingReason: files[0].missingReason || primaryFilePatch.missingReason,
    resourceKey: files[0].resourceKey || primaryFilePatch.resourceKey,
    mimeType: files[0].mimeType || primaryFilePatch.mimeType,
    relativePath: files[0].relativePath || primaryFilePatch.relativePath,
    originFileName: files[0].originFileName || primaryFilePatch.originFileName,
  };

  return files;
};

export const getPrimaryTaskFile = (taskLike: any): CHAT.TFile | undefined => {
  return getTaskFiles(taskLike)[0];
};

/**
 * file/get 的历史回放有时只有正文和主文件名，不一定还能恢复出完整附件引用。
 * 这里统一补一个文件名兜底，保证工作区和动作标题都能明确展示“读取了哪个文件”。
 */
export const getPrimaryTaskFileName = (taskLike: any): string => {
  const primaryFile = getPrimaryTaskFile(taskLike);
  if (primaryFile?.name?.trim()) {
    return primaryFile.name.trim();
  }

  const nestedResultMap = readNestedResultMap(taskLike);
  return firstText(
    taskLike?.resultMap?.primaryFileName,
    nestedResultMap?.primaryFileName,
    taskLike?.resultMap?.fileName,
    nestedResultMap?.fileName,
    taskLike?.resultMap?.filename,
    nestedResultMap?.filename
  );
};
