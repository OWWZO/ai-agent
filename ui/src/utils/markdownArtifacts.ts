import { getTaskFiles } from "@/utils/taskArtifacts";

/**
 * 终答 Markdown 里的相对文件引用解析。
 *
 * Agent 只需写出文件名（如 `![](chart.png)` / `[报告](report.md)`），
 * 展示层用本轮产物表（artifactRefs / fileInfo）解析成真实 preview/download URL，
 * 禁止前端硬编码 FILE_SERVER_URL 前缀。
 */

const ABSOLUTE_OR_SPECIAL_URL_PATTERN =
  /^(?:[a-z][a-z0-9+.-]*:|\/\/|#|data:|mailto:|tel:)/i;

/** 终答 Markdown 内嵌媒体：与 FileRenderer 扩展名集合对齐 */
const MARKDOWN_VIDEO_EXTENSIONS = new Set(["mp4", "mov", "webm", "m4v", "ogv"]);
const MARKDOWN_AUDIO_EXTENSIONS = new Set([
  "mp3",
  "wav",
  "ogg",
  "m4a",
  "aac",
  "flac",
  "opus",
]);

export type MarkdownMediaKind = "video" | "audio" | "image" | "other";

const toText = (value: unknown) => {
  if (value == null) {
    return "";
  }
  return String(value).trim();
};

const basenameOf = (pathLike: string) => {
  const normalized = pathLike.replace(/\\/g, "/");
  const segments = normalized.split("/");
  return segments[segments.length - 1] || normalized;
};

/**
 * 从 URL 或文件名取扩展名（忽略 query/hash）。
 */
export function resolveMarkdownMediaExtension(href?: string | null): string {
  const text = toText(href);
  if (!text) {
    return "";
  }
  let pathPart = text;
  try {
    if (/^[a-z][a-z0-9+.-]*:/i.test(text) || text.startsWith("//")) {
      pathPart = new URL(text, "https://workspace.local").pathname;
    } else {
      pathPart = text.split("#")[0]?.split("?")[0] || text;
    }
  } catch {
    pathPart = text.split("#")[0]?.split("?")[0] || text;
  }
  const base = basenameOf(pathPart);
  const dot = base.lastIndexOf(".");
  if (dot < 0 || dot === base.length - 1) {
    return "";
  }
  return base.slice(dot + 1).toLowerCase();
}

/**
 * 终答里 ![]() / []() 指向媒体文件时，用 video/audio 播放器而非 img/链接。
 */
export function resolveMarkdownMediaKind(href?: string | null): MarkdownMediaKind {
  const ext = resolveMarkdownMediaExtension(href);
  if (!ext) {
    return "other";
  }
  if (MARKDOWN_VIDEO_EXTENSIONS.has(ext)) {
    return "video";
  }
  if (MARKDOWN_AUDIO_EXTENSIONS.has(ext)) {
    return "audio";
  }
  return "other";
}

/**
 * 协议链接、锚点、data URI 等不走产物解析，避免误伤外链。
 */
export function isAbsoluteOrSpecialMarkdownUrl(href?: string | null): boolean {
  const text = toText(href);
  if (!text) {
    return true;
  }
  return ABSOLUTE_OR_SPECIAL_URL_PATTERN.test(text);
}

/**
 * 将 Markdown 引用路径收敛为可匹配的文件名（去掉 ./、query、hash）。
 */
export function normalizeMarkdownArtifactRef(href?: string | null): string {
  const text = toText(href);
  if (!text || isAbsoluteOrSpecialMarkdownUrl(text)) {
    return "";
  }

  let decoded = text;
  try {
    decoded = decodeURIComponent(text);
  } catch {
    decoded = text;
  }

  const withoutQuery = decoded.split("#")[0]?.split("?")[0] || "";
  const stripped = withoutQuery.replace(/^\.\//, "").replace(/^\//, "").trim();
  return basenameOf(stripped);
}

const fileLookupKeys = (file: CHAT.TFile): string[] => {
  const keys = new Set<string>();
  const name = toText(file.name).toLowerCase();
  if (name) {
    keys.add(name);
    keys.add(basenameOf(name).toLowerCase());
  }
  const resourceKey = toText(file.resourceKey).toLowerCase();
  if (resourceKey) {
    keys.add(resourceKey);
    keys.add(basenameOf(resourceKey).toLowerCase());
  }
  return [...keys].filter(Boolean);
};

/**
 * 后写覆盖先写：同名多次生成时优先命中较新的产物。
 * missing 文件不参与解析，避免把失效地址塞进 img/a。
 */
export function buildArtifactFileLookup(
  files?: CHAT.TFile[] | null
): Map<string, CHAT.TFile> {
  const lookup = new Map<string, CHAT.TFile>();
  for (const file of files || []) {
    if (!file || file.missing) {
      continue;
    }
    const resolvedUrl = toText(file.url) || toText(file.downloadUrl);
    if (!resolvedUrl) {
      continue;
    }
    for (const key of fileLookupKeys(file)) {
      lookup.set(key, file);
    }
  }
  return lookup;
}

export function resolveMarkdownArtifactHref(
  href?: string | null,
  files?: CHAT.TFile[] | null
): string {
  const raw = toText(href);
  if (!raw || isAbsoluteOrSpecialMarkdownUrl(raw)) {
    return raw;
  }

  const lookupName = normalizeMarkdownArtifactRef(raw);
  if (!lookupName) {
    return raw;
  }

  const lookup = buildArtifactFileLookup(files);
  const matched =
    lookup.get(lookupName.toLowerCase()) ||
    lookup.get(basenameOf(lookupName).toLowerCase());

  if (!matched) {
    return raw;
  }

  return toText(matched.url) || toText(matched.downloadUrl) || raw;
}

const CODE_FENCE_SEGMENT_PATTERN = /(```[\s\S]*?```)/g;
// ![alt](path) 与 [text](path)，排除已是协议/锚点的目标
const MARKDOWN_LINK_OR_IMAGE_PATTERN =
  /(!?\[[^\]]*]\()([^)\s]+)(\))/g;

/**
 * 渲染前把相对文件引用替换为产物 URL。
 * 比仅改 img/a 组件更稳：Streamdown 与浏览器默认会把 report.md 解析成当前页 origin。
 */
export function rewriteMarkdownArtifactRefs(
  content?: string | null,
  files?: CHAT.TFile[] | null
): string {
  const source = content ?? "";
  if (!source || !files?.length) {
    return source;
  }

  return source
    .split(CODE_FENCE_SEGMENT_PATTERN)
    .map((segment) => {
      if (segment.startsWith("```")) {
        return segment;
      }
      return segment.replace(
        MARKDOWN_LINK_OR_IMAGE_PATTERN,
        (full, prefix: string, href: string, suffix: string) => {
          const resolved = resolveMarkdownArtifactHref(href, files);
          if (!resolved || resolved === href) {
            return full;
          }
          return `${prefix}${resolved}${suffix}`;
        }
      );
    })
    .join("");
}

const pushTaskFiles = (bucket: CHAT.TFile[], taskLike: unknown) => {
  for (const file of getTaskFiles(taskLike)) {
    bucket.push(file);
  }
};

/**
 * 时间线里文件多挂在 Agent/容器的 children 上，必须深度遍历，否则终答相对引用解析不到。
 */
const walkTaskTree = (bucket: CHAT.TFile[], taskLike: unknown) => {
  if (!taskLike || typeof taskLike !== "object") {
    return;
  }
  pushTaskFiles(bucket, taskLike);
  const children = (taskLike as CHAT.Task).children;
  if (!Array.isArray(children) || !children.length) {
    return;
  }
  for (const child of children) {
    walkTaskTree(bucket, child);
  }
};

const walkTaskGroups = (bucket: CHAT.TFile[], groups?: unknown) => {
  if (!Array.isArray(groups)) {
    return;
  }
  for (const group of groups) {
    if (!Array.isArray(group)) {
      // multiAgent 原始事实有时是扁平 task 列表
      walkTaskTree(bucket, group);
      continue;
    }
    for (const task of group) {
      walkTaskTree(bucket, task);
    }
  }
};

const dedupeArtifactFiles = (collected: CHAT.TFile[]): CHAT.TFile[] => {
  const dedup = new Map<string, CHAT.TFile>();
  for (const file of collected) {
    const key =
      toText(file.resourceKey) ||
      toText(file.url) ||
      toText(file.downloadUrl) ||
      toText(file.name);
    if (!key) {
      continue;
    }
    // 后写覆盖：同名/同 key 优先较新产物
    dedup.set(key, file);
  }
  return [...dedup.values()];
};

/**
 * 从单轮对话收集可用于 Markdown 解析的产物文件。
 * 覆盖用户上传 files、派生 tasks（含 children）、multiAgent、结论与 generatedFiles。
 */
export function collectChatArtifactFiles(
  chat?: Pick<
    CHAT.ChatItem,
    "tasks" | "conclusion" | "generatedFiles" | "multiAgent" | "files"
  > | null
): CHAT.TFile[] {
  if (!chat) {
    return [];
  }

  const collected: CHAT.TFile[] = [];

  // 用户本轮上传：终答 ![](upload.png) 与会话文件列表都依赖此来源
  for (const file of chat.files || []) {
    if (file) {
      collected.push(file);
    }
  }

  walkTaskGroups(collected, chat.tasks);
  walkTaskGroups(collected, chat.multiAgent?.tasks);

  if (chat.conclusion) {
    walkTaskTree(collected, chat.conclusion);
  }

  for (const file of chat.generatedFiles || []) {
    if (file) {
      collected.push(file);
    }
  }

  return dedupeArtifactFiles(collected);
}

/**
 * 会话级产物：跨多轮聚合，供终答相对引用与文件侧栏共用。
 */
export function collectSessionArtifactFiles(
  chatList?: Array<
    Pick<
      CHAT.ChatItem,
      "tasks" | "conclusion" | "generatedFiles" | "multiAgent" | "files"
    > | null | undefined
  > | null
): CHAT.TFile[] {
  const collected: CHAT.TFile[] = [];
  for (const chat of chatList || []) {
    for (const file of collectChatArtifactFiles(chat)) {
      collected.push(file);
    }
  }
  return dedupeArtifactFiles(collected);
}
