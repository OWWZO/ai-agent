import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";

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
 * 不同来源的任务附件可能只带 fileInfo，也可能只带 artifactRefs。
 * 这里统一归一化成前端稳定的文件结构，避免各组件各写一套兜底逻辑。
 */
export const normalizeTaskFile = (raw: any): CHAT.TFile | null => {
  if (!raw || typeof raw !== "object") {
    return null;
  }

  const previewUrl = normalizeFileUrlForBrowser(firstText(
    raw.previewUrl,
    raw.domainUrl,
    raw.url,
    raw.ossUrl,
    raw.downloadUrl
  ));
  const downloadUrl = normalizeFileUrlForBrowser(firstText(
    raw.downloadUrl,
    raw.ossUrl,
    raw.domainUrl,
    raw.url,
    raw.previewUrl
  ));
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

const readResultMapFile = (resultMap?: Record<string, unknown>) => {
  if (!resultMap || typeof resultMap !== "object") {
    return null;
  }

  const previewUrl = normalizeFileUrlForBrowser(
    firstText(resultMap.previewUrl, resultMap.domainUrl, resultMap.url)
  );
  const downloadUrl = normalizeFileUrlForBrowser(firstText(
    resultMap.downloadUrl,
    resultMap.ossUrl,
    resultMap.previewUrl,
    resultMap.domainUrl,
    resultMap.url
  ));
  const primaryFileName = firstText(
    resultMap.primaryFileName,
    resultMap.fileName,
    resultMap.filename,
    resultMap.displayName,
    resultMap.name
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

const readPrimaryResultMapFile = (taskLike: any) => {
  const nestedResultMap = readNestedResultMap(taskLike);
  const nestedFile = readResultMapFile(nestedResultMap);
  const currentFile = readResultMapFile(taskLike?.resultMap);

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
  const candidates = [
    taskLike?.artifactRefs,
    taskLike?.fileInfo,
    taskLike?.fileList,
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
    const file = normalizeTaskFile(raw);
    if (!file) {
      return;
    }
    const key = file.resourceKey || file.downloadUrl || file.url || file.name;
    if (!key) {
      return;
    }
    dedup.set(key, file);
  });

  const files = Array.from(dedup.values());
  const primaryFilePatch = normalizeTaskFile(readPrimaryResultMapFile(taskLike));

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
