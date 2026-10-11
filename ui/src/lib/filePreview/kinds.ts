import { extractExtension, isKnownCodeExtension, normalizeExtension } from "./language";

/**
 * 文件预览类型 —— 全项目唯一真相。
 *
 * 历史上「该用哪个 renderer」散落在 `useMsgTypes` / `panelResolver` / `FileRenderer` /
 * `FileList.renderContent` / `taskArtifacts` 五处，各自维护一份扩展名表，已经出现过
 * `.xlsm` 漏判这类不一致。所有判定从此收敛到 `resolveFilePreviewKind()`。
 */
export type FilePreviewKind =
  | "markdown"
  | "svg"
  | "image"
  | "video"
  | "audio"
  | "html"
  | "pdf"
  | "docx"
  | "doc"
  | "xlsx"
  | "csv"
  | "pptx"
  | "model3d"
  | "diff"
  | "code"
  | "text"
  | "binary"
  | "unknown";

const toSet = (list: string): ReadonlySet<string> =>
  new Set(list.split(" ").filter(Boolean));

/** Markdown 文档。 */
export const MARKDOWN_EXTENSIONS = toSet("md markdown mdx");
/** SVG —— 单独成类：支持「图形 / 源码」双模式，不能当普通位图处理。 */
export const SVG_EXTENSIONS = toSet("svg svg+xml");
/** 位图。 */
export const IMAGE_EXTENSIONS = toSet("png jpg jpeg gif webp bmp ico avif apng");
/** 视频。 */
export const VIDEO_EXTENSIONS = toSet("mp4 mov webm m4v");
/** 音频。 */
export const AUDIO_EXTENSIONS = toSet("mp3 wav m4a ogg opus flac weba");
export const HTML_EXTENSIONS = toSet("html htm");
export const PDF_EXTENSIONS = toSet("pdf");
export const DOCX_EXTENSIONS = toSet("docx");
/** 老式 Word（二进制 .doc），浏览器端不做解析，走下载引导。 */
export const LEGACY_DOC_EXTENSIONS = toSet("doc");
export const XLSX_EXTENSIONS = toSet("xlsx xlsm xls");
export const CSV_EXTENSIONS = toSet("csv tsv");
export const PPTX_EXTENSIONS = toSet("ppt pptx pps ppsx");
export const MODEL3D_EXTENSIONS = toSet("glb gltf");
/** 结构化 diff 文本。 */
export const DIFF_EXTENSIONS = toSet("diff patch");

/**
 * 已知二进制：明确不给在线预览，直接引导下载。
 * 注意这里**不含**图片 / 音视频 / Office —— 那些都有专门 renderer。
 */
export const BINARY_EXTENSIONS = toSet(
  [
    // 压缩包
    "zip",
    "rar",
    "7z",
    "tar",
    "gz",
    "tgz",
    "bz2",
    "xz",
    // 可执行 / 库
    "exe",
    "dll",
    "so",
    "dylib",
    "dmg",
    "pkg",
    "apk",
    "ipa",
    "msi",
    // 字体
    "woff",
    "woff2",
    "ttf",
    "otf",
    "eot",
    // 浏览器无法直接播放 / 预览的媒体容器
    "aac",
    "avi",
    "mkv",
    "wmv",
    "mpeg",
    "mpg",
    "flv",
    "rmvb",
    // 其他
    "bin",
    "dat",
    "wasm",
    "db",
    "sqlite",
    "psd",
    "ai",
    "sketch",
    "ttc",
  ].join(" ")
);

/** 扩展名 → kind 的查找表（由上面的集合反推，保证只有一份真相）。 */
const KIND_BY_EXTENSION: ReadonlyMap<string, FilePreviewKind> = (() => {
  const table = new Map<string, FilePreviewKind>();
  const groups: ReadonlyArray<readonly [FilePreviewKind, ReadonlySet<string>]> = [
    ["markdown", MARKDOWN_EXTENSIONS],
    ["svg", SVG_EXTENSIONS],
    ["image", IMAGE_EXTENSIONS],
    ["video", VIDEO_EXTENSIONS],
    ["audio", AUDIO_EXTENSIONS],
    ["html", HTML_EXTENSIONS],
    ["pdf", PDF_EXTENSIONS],
    ["docx", DOCX_EXTENSIONS],
    ["doc", LEGACY_DOC_EXTENSIONS],
    ["xlsx", XLSX_EXTENSIONS],
    ["csv", CSV_EXTENSIONS],
    ["pptx", PPTX_EXTENSIONS],
    ["model3d", MODEL3D_EXTENSIONS],
    ["diff", DIFF_EXTENSIONS],
    ["binary", BINARY_EXTENSIONS],
  ];
  for (const [kind, extensions] of groups) {
    for (const extension of extensions) {
      table.set(extension, kind);
    }
  }
  return table;
})();

/**
 * MIME → kind 兜底表。**顺序敏感**：前缀匹配项（`image/`）必须排在精确项之后。
 */
const KIND_BY_MIME: ReadonlyArray<readonly [string, FilePreviewKind]> = [
  ["text/markdown", "markdown"],
  ["image/svg+xml", "svg"],
  ["application/pdf", "pdf"],
  [
    "application/vnd.openxmlformats-officedocument.wordprocessingml",
    "docx",
  ],
  ["application/msword", "doc"],
  [
    "application/vnd.openxmlformats-officedocument.spreadsheetml",
    "xlsx",
  ],
  ["application/vnd.ms-excel", "xlsx"],
  ["text/csv", "csv"],
  ["text/tab-separated-values", "csv"],
  [
    "application/vnd.openxmlformats-officedocument.presentationml",
    "pptx",
  ],
  ["application/vnd.ms-powerpoint", "pptx"],
  ["model/gltf", "model3d"],
  ["text/html", "html"],
  ["image/", "image"],
  ["video/", "video"],
  ["audio/", "audio"],
  ["text/", "text"],
];

export interface FilePreviewSource {
  /** 文件名（优先）或路径。 */
  name?: string | null;
  /** MIME 类型。 */
  mimeType?: string | null;
  /** 文件 URL（name 缺失时用于取扩展名）。 */
  url?: string | null;
}

function resolveKindByMime(mimeType?: string | null): FilePreviewKind | null {
  const mime = String(mimeType ?? "").trim().toLowerCase();
  if (!mime || mime.includes("/") === false) return null;
  for (const [prefix, kind] of KIND_BY_MIME) {
    if (mime.startsWith(prefix)) return kind;
  }
  return null;
}

/**
 * 解析文件预览类型。
 *
 * 判定优先级：**扩展名 → MIME → 文本兜底**。
 * 扩展名命中即返回（扩展名比 MIME 更可靠：很多后端会把 .md 标成 `application/octet-stream`）；
 * 扩展名未命中时用 MIME 兜底；两者都没有时按 `unknown` 处理。
 */
export function resolveFilePreviewKind(
  source?: FilePreviewSource | string | null
): FilePreviewKind {
  const normalized: FilePreviewSource =
    typeof source === "string" ? { name: source } : source ?? {};

  const extension =
    extractExtension(normalized.name) || extractExtension(normalized.url);

  if (extension) {
    const byExtension = KIND_BY_EXTENSION.get(extension);
    if (byExtension) return byExtension;
    // 已知代码扩展名 → code；其余未知扩展名 → 文本兜底（与历史行为一致）。
    if (isKnownCodeExtension(extension)) return "code";
    const byMime = resolveKindByMime(normalized.mimeType);
    if (byMime) return byMime;
    return "text";
  }

  const byMime = resolveKindByMime(normalized.mimeType);
  if (byMime) return byMime;

  return "unknown";
}

/** 归一化扩展名（供需要单独取扩展名的调用方复用，避免各自实现）。 */
export function resolvePreviewExtension(value?: string | null): string {
  return normalizeExtension(extractExtension(value) || value);
}

/** 是否属于「服务端解码后给文本」的类型（渲染前需要 fetch 成字符串）。 */
export function isTextBackedKind(kind: FilePreviewKind): boolean {
  return (
    kind === "markdown" ||
    kind === "svg" ||
    kind === "html" ||
    kind === "code" ||
    kind === "text" ||
    kind === "csv" ||
    kind === "diff"
  );
}

/** 是否属于「直接吃二进制 / 交给专门引擎」的类型。 */
export function isBinaryBackedKind(kind: FilePreviewKind): boolean {
  return (
    kind === "image" ||
    kind === "video" ||
    kind === "audio" ||
    kind === "pdf" ||
    kind === "docx" ||
    kind === "doc" ||
    kind === "xlsx" ||
    kind === "pptx" ||
    kind === "model3d" ||
    kind === "binary"
  );
}
