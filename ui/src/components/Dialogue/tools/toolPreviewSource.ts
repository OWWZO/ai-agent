/**
 * 工具卡 → 右侧预览的**内容**判定（Stage 3/4 共用）。
 *
 * 与 `EditToolCall` 里的 `ToolPreviewRequest` 区分：那个是「用户点了哪张卡」的载荷
 * （task + chat + fileIndex），本模块是「该展示什么内容」的判定结果。
 * 两者配合：点击载荷 → 本模块 → `CodeViewerSource` → 面板 / 侧栏 tab。
 *
 * 之所以独立成模块（而不是留在 `ChatView/index.tsx`）：ChatView 的模块图很重
 * （拉入整条对话渲染链），放这里才能在 node 环境直接单测判定逻辑。
 */

import type { CodeViewerSource } from "@/lib/codeViewerSource";
import {
  buildEditCodeViewerSource,
  buildReadCodeViewerSource,
  buildTextCodeViewerSource,
  pathBaseName,
} from "@/lib/codeViewerSource";
import { resolveTaskToolResult } from "@/utils/chat/toolCalls";
import { extractToolFilePath } from "./toolMeta";
import {
  buildEditDiffCode,
  extractEditFileSummaries,
  extractEditPath,
} from "./toolDiff";
import { normalizeToolName, toolLabel } from "./toolMeta";
import {
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "./toolTaskAdapter";

/** 右侧预览的完整描述：内容来源 + 运行时才知道的附加信息。 */
export type ToolPreviewSource = {
  source: CodeViewerSource;
  /** `file` 类型的可读 URL（能映射到会话产物时才有值） */
  fileUrl?: string;
  /** 降级原因（不在会话产物中 / 已失效 / 超体积上限） */
  unavailableReason?: string;
  /** 工具执行输出，patch 之外的兜底内容 */
  output?: string[];
  status?: "running" | "ok" | "error";
  /** 源文件路径（供侧栏判断能否并进同 key 的文件 tab） */
  path?: string;
};

/** 产物文件的最小形状（避免依赖 ChatView 的 WorkspaceFileItem 类型）。 */
type ArtifactLike = {
  resourceKey?: string;
  relativePath?: string;
  originFileName?: string;
  name?: string;
  previewUrl?: string;
  url?: string;
  downloadUrl?: string;
};

/** 按路径后缀在产物清单里找文件（与 `findWorkspaceFileByPath` 同语义的轻量版）。 */
function findArtifactByPath<T extends ArtifactLike>(
  files: T[],
  path: string
): T | undefined {
  const normalize = (value?: string | null) =>
    (value || "").replace(/\\/g, "/").replace(/^\.\/+/, "").replace(/^\/+/, "");
  const target = normalize(path);
  if (!target) return undefined;

  let suffixHit: T | undefined;
  for (const file of files) {
    const relative = normalize(
      file.relativePath || file.originFileName || file.name
    );
    if (!relative) continue;
    if (relative === target) return file;
    if (
      !suffixHit &&
      (target.endsWith(`/${relative}`) || relative.endsWith(`/${target}`))
    ) {
      suffixHit = file;
    }
  }
  return suffixHit;
}

function artifactUrl(file?: ArtifactLike): string {
  if (!file) return "";
  return file.previewUrl || file.url || file.downloadUrl || "";
}

/** 从 write 的入参里取要写入的正文。 */
function extractWriteContent(arg: string): string | undefined {
  const trimmed = (arg || "").trim();
  if (!trimmed.startsWith("{")) return undefined;
  try {
    const parsed = JSON.parse(trimmed);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return undefined;
    }
    const record = parsed as Record<string, unknown>;
    const content = record.content ?? record.contents ?? record.text;
    return typeof content === "string" ? content : undefined;
  } catch {
    return undefined;
  }
}

/**
 * 从 read 工具的结果里取文件正文。
 *
 * Reactor-agent 的 `workspace_read` 返回结构化结果
 * `{ type: "text", path, startLine, endLine, numLines, totalLines, content, truncated? }`
 * —— **正文就在结果里**，不需要再去会话产物清单里找 URL。
 * 这条路径覆盖「读工作区任意文件」，正好绕开决策点 B（前端没有按绝对路径读文件的接口）。
 *
 * `type === "file_unchanged"` 时后端只回一个占位 stub、不带 content，返回 null 让调用方回退。
 */
function extractReadResult(tool: CHAT.Task): { content: string; path?: string } | null {
  const raw = resolveTaskToolResult(tool)?.toolResult;
  if (!raw || typeof raw !== "string") return null;
  const trimmed = raw.trim();
  if (!trimmed.startsWith("{")) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;

  const record = parsed as Record<string, unknown>;
  if (record.type === "file_unchanged") return null;
  const content = typeof record.content === "string" ? record.content : undefined;
  if (!content) return null;

  return {
    content,
    path: typeof record.path === "string" ? record.path : undefined,
  };
}

/**
 * 从工具任务算出「该在右侧展示什么」。
 *
 * 纯函数（无 hook / 无闭包状态），因此「点开的 tab」与「独立面板」共用同一份判定，
 * 不会出现两条路径显示不同内容。
 *
 * 分三种情况：
 * 1. 多文件 edit + `fileIndex` → 取该文件自己的 patch（否则永远只看第一个文件）；
 * 2. `read` → 打开文件内容（不是 diff）；
 * 3. 其余 → 按 edit 语义构造 patch，无 patch 时退化为 file source。
 */
export function buildToolPreviewSource(
  task: CHAT.Task,
  fileIndex?: number,
  artifactFiles?: ArtifactLike[]
): ToolPreviewSource | null {
  const name = resolveTaskToolName(task);
  const arg = resolveTaskToolArg(task);
  const output = resolveTaskToolOutput(task);
  const status = resolveTaskToolStatus(task);

  // 1. 多文件 edit：按 fileIndex 取该文件自己的 patch / 路径。
  const summaries = extractEditFileSummaries(name, arg);
  const picked = fileIndex != null ? summaries[fileIndex] : undefined;
  if (picked) {
    return {
      source: buildEditCodeViewerSource({
        path: picked.path,
        fileName: picked.fileName,
        patch: picked.patch,
      }),
      path: picked.path,
      output,
      status,
    };
  }

  // 2. read：打开文件内容（不是 diff）。
  if (normalizeToolName(name) === "read") {
    const argPath = extractToolFilePath(arg);
    const result = extractReadResult(task);
    const readPath = result?.path || argPath;

    // 2a. 结果里自带正文 → 直接出 text source。
    //     这条路不依赖会话产物清单，工作区里读过的任何文件都能预览。
    if (result && readPath) {
      return {
        source: buildTextCodeViewerSource({
          title: pathBaseName(readPath) || readPath,
          path: readPath,
          content: result.content,
        }),
        path: readPath,
        output,
        status,
      };
    }

    // 2b. 没拿到正文（file_unchanged / 历史数据）→ 退回产物 URL 链路。
    if (readPath) {
      const hit = findArtifactByPath(artifactFiles || [], readPath);
      return {
        source: buildReadCodeViewerSource({ path: readPath }),
        path: readPath,
        fileUrl: artifactUrl(hit) || undefined,
        unavailableReason: hit
          ? undefined
          : `该文件不在会话产物中，无法预览：${readPath}`,
        output,
        status,
      };
    }
  }

  // 3. 其余：按 edit 语义构造。
  const patch = buildEditDiffCode({
    name,
    arg,
  });
  const path = extractEditPath(arg);

  // 3a. write 没有 old_string，构造不出 diff；直接展示「写进去的正文」，
  //     不依赖产物 URL 是否已就绪（workspace_write 的返回里只有 chars，没有 content）。
  if (normalizeToolName(name) === "write" && path) {
    const content = extractWriteContent(arg);
    if (content !== undefined) {
      return {
        source: buildTextCodeViewerSource({
          title: pathBaseName(path) || path,
          path,
          content,
        }),
        path,
        output,
        status,
      };
    }
  }

  return {
    source: buildEditCodeViewerSource({
      path: path || toolLabel(name),
      fileName: path ? pathBaseName(path) : toolLabel(name),
      patch: patch || undefined,
    }),
    path: path || undefined,
    output,
    status,
  };
}
