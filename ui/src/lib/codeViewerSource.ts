/**
 * 右侧预览区「展示什么」的统一契约（对齐 ZCode `lib/codeViewer.ts` 的裁剪版）。
 *
 * 只保留 Cute-agent 当前需要的 4 种形态：
 * - `patch` 编辑补丁（edit / multi_edit 的 old_string→new_string）
 * - `file`  真实文件（read 工具打开磁盘文件、聊天产物文件）
 * - `text`  纯文本（无落盘路径的内联内容）
 * - `image` 图片
 *
 * 唯一职责是「描述来源」，不负责渲染。渲染分派在 `ToolPreviewPanel`，
 * 侧栏 tab 分派在 `ActionView`（走 `sourceTabKey` / `sourceTabMeta`）。
 */

import { resolveCodeLanguage } from "./filePreview/language";

export type CodeViewerSource =
  | { type: "file"; title: string; path: string }
  | { type: "patch"; title: string; path?: string; patch: string }
  | {
      type: "text";
      title: string;
      path?: string;
      content: string;
      language: string;
    }
  | { type: "image"; title: string; path: string };

/** 从路径里取最后一段作为展示名。 */
export function pathBaseName(path: string): string {
  const trimmed = path.replace(/[/\\]+$/, "");
  const idx = Math.max(trimmed.lastIndexOf("/"), trimmed.lastIndexOf("\\"));
  return idx >= 0 ? trimmed.slice(idx + 1) : trimmed;
}

/**
 * 把路径拆成「父级段 + 文件名」，供单行面包屑渲染。
 *
 * 对齐 ZCode `PreviewPane.tsx:104 normalizeBreadcrumbPathSegments` +
 * `:1546-1568` 的 `rootLabel › parents › fileLabel` 结构。
 * 只有裸文件名（如 `generate_fixtures.py`）时 `parents` 为空 —— 此时**不要**
 * 再把同一个名字当副标题重复渲染一遍。
 */
export function pathBreadcrumb(path: string): {
  parents: string[];
  file: string;
} {
  const normalized = path.replace(/\\/g, "/").replace(/^\/+|\/+$/g, "");
  if (!normalized) {
    return {
      parents: [],
      file: "",
    };
  }
  const parts = normalized.split("/").filter(Boolean);
  const file = parts.pop() ?? "";
  return {
    parents: parts,
    file,
  };
}

/** 短哈希（FNV-1a 32bit），用于 patch tab 去重键，避免引入异步 crypto。 */
function shortHash(input: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(36);
}

/**
 * 对齐 ZCode `ToolCallBlocks/renderers/edit.tsx:43`：
 * `path` 必须取真实路径（`summary.path`），**不能**用展示用的目录名 `filePath`，
 * 否则右侧预览会去读一个不存在的路径。
 */
export function buildEditCodeViewerSource(summary: {
  path: string;
  fileName: string;
  patch?: string;
}): CodeViewerSource {
  const title = summary.fileName || pathBaseName(summary.path) || summary.path;
  if (summary.patch) {
    return {
      type: "patch",
      title,
      path: summary.path,
      patch: summary.patch
    };
  }
  return {
    type: "file",
    title,
    path: summary.path
  };
}

/** read 工具：路径 + 展示名 → file source。 */
export function buildReadCodeViewerSource(summary: {
  path: string;
  fileName?: string;
}): CodeViewerSource {
  return {
    type: "file",
    title: summary.fileName || pathBaseName(summary.path) || summary.path,
    path: summary.path,
  };
}

/** 无落盘路径的内联文本 → text source（language 缺失时按扩展名推断）。 */
export function buildTextCodeViewerSource(summary: {
  title: string;
  path?: string;
  content: string;
  language?: string;
}): CodeViewerSource {
  return {
    type: "text",
    title: summary.title,
    path: summary.path,
    content: summary.content,
    language:
      summary.language || resolveCodeLanguage(summary.path || summary.title),
  };
}

/**
 * tab 去重键。
 * - `patch` 按「路径 + 内容哈希」：同一文件同一补丁不重复开 tab，
 *   内容变了（重跑工具）则视为新 tab。
 * - 其余按路径（无路径时退化为标题）。
 */
export function sourceTabKey(source: CodeViewerSource): string {
  if (source.type === "patch") {
    const base = source.path || source.title;
    return `patch:${base}:${shortHash(source.patch)}`;
  }
  if (source.type === "text") {
    return `text:${source.path || source.title}:${shortHash(source.content)}`;
  }
  return `${source.type}:${source.path}`;
}

/** tab 条展示元信息（图标/标题/副标题由 ActionView 决定，这里只出数据）。 */
export function sourceTabMeta(source: CodeViewerSource): {
  title: string;
  subtitle?: string;
  kind: CodeViewerSource["type"];
} {
  if (source.type === "patch") {
    return {
      title: source.title,
      subtitle: source.path || "补丁",
      kind: source.type,
    };
  }
  if (source.type === "text") {
    return {
      title: source.title,
      subtitle: source.path || source.language,
      kind: source.type,
    };
  }
  return {
    title: source.title,
    subtitle: source.path,
    kind: source.type
  };
}
