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

const toSize = (value: unknown) => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const toExtension = (name: string, fallbackType?: string) => {
  const ext = name.split(".").pop()?.toLowerCase();
  if (ext) {
    return ext;
  }
  return (fallbackType || "").toLowerCase();
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

const normalizeExtension = (value?: string | null) => {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/^\./, "");
};

/**
 * 历史详情里既可能返回旧 fileInfo，也可能只返回 canonical artifactRefs。
 * 这里统一归一化成前端稳定的文件结构，避免各组件自己写一套兜底逻辑。
 */
export const normalizeHistoryFile = (raw: any): CHAT.TFile | null => {
  if (!raw || typeof raw !== "object") {
    return null;
  }

  const previewUrl = firstText(
    raw.previewUrl,
    raw.domainUrl,
    raw.url,
    raw.ossUrl,
    raw.downloadUrl
  );
  const downloadUrl = firstText(
    raw.downloadUrl,
    raw.ossUrl,
    raw.domainUrl,
    raw.url,
    raw.previewUrl
  );
  const resourceKey = firstText(
    raw.resourceKey,
    raw.ossUrl,
    raw.downloadUrl,
    raw.domainUrl,
    raw.fileName,
    raw.displayName,
    raw.name
  );
  const name = firstText(
    raw.displayName,
    raw.fileName,
    raw.name,
    resourceKey,
    "未命名文件"
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
  };
};

export const artifactRefsToFileInfo = (artifactRefs?: unknown[]) => {
  if (!Array.isArray(artifactRefs) || !artifactRefs.length) {
    return [];
  }

  return artifactRefs
    .map((artifact) => normalizeHistoryFile(artifact))
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

const readNestedResultMap = (taskLike: any) => {
  const nested = taskLike?.resultMap?.resultMap;
  return nested && typeof nested === "object" ? nested : undefined;
};

const readRawFiles = (taskLike: any) => {
  if (!taskLike || typeof taskLike !== "object") {
    return [];
  }

  const nestedResultMap = readNestedResultMap(taskLike);
  const candidates = [
    taskLike?.artifactRefs,
    taskLike?.resultMap?.artifactRefs,
    nestedResultMap?.artifactRefs,
    taskLike?.resultMap?.fileInfo,
    taskLike?.resultMap?.fileList,
    nestedResultMap?.fileInfo,
    nestedResultMap?.fileList,
  ];

  for (const candidate of candidates) {
    if (Array.isArray(candidate) && candidate.length) {
      return candidate;
    }
  }

  return [];
};

export const getTaskFiles = (taskLike: any): CHAT.TFile[] => {
  const dedup = new Map<string, CHAT.TFile>();

  readRawFiles(taskLike).forEach((raw) => {
    const file = normalizeHistoryFile(raw);
    if (!file) {
      return;
    }
    const key = file.resourceKey || file.downloadUrl || file.url || file.name;
    if (!key) {
      return;
    }
    dedup.set(key, file);
  });

  return Array.from(dedup.values());
};

export const getPrimaryTaskFile = (taskLike: any): CHAT.TFile | undefined => {
  return getTaskFiles(taskLike)[0];
};
