import { fileKindBadge, resolveFileKind } from "@/utils/fileKind";
import {
  formatDiffStats,
  patchDiffStats,
} from "@/components/Dialogue/tools/toolDiff";
import { resolveTaskResultMap } from "@/utils/chat/toolCalls";
import { sourceTabMeta } from "@/lib/codeViewerSource";
import {
  isAgentDirectoryTab,
  isAgentTab,
  isSourceTab,
  isTaskTab,
  workspaceTabKey,
  type WorkspaceFileItem,
  type WorkspaceTab,
} from "./workspaceFiles";

/**
 * tab 的展示解析 —— **tab 条与「标签页列表」浮层共用这一份**。
 *
 * 两处各写一遍是上一版的隐患：条上是「徽章 + 标题」，浮层里如果自己再判一次
 * `isAgentTab`，加一种 tab 类型时就会漏改一处（条上有图标、列表里没有）。
 *
 * 这里是**纯数据**（图标只出 spec，不渲染 JSX），渲染在 `WorkspaceTabIcon.tsx`：
 * 文件既导出组件又导出普通函数会让 react-refresh 失效。
 */

/** 图标规格：文件类型角标，或 lucide 字形 */
export type WorkspaceTabIconSpec =
  | { kind: "badge"; badge: string }
  | { kind: "glyph"; glyph: "bot" | "list-tree" | "activity" };

export type WorkspaceTabDisplay = {
  key: string;
  title: string;
  icon: WorkspaceTabIconSpec;
  /** 搜索兜底：路径 / 副标题（权重 40） */
  hint?: string;
  /** 搜索兜底：类型词（权重 20） */
  typeHint?: string;
  /** patch tab 的 `+A −B`；无则空串 */
  diffBadge?: string;
};

/** source tab 的类型词，供搜索时按「补丁 / 文本」找 */
const SOURCE_TYPE_LABEL: Record<string, string> = {
  file: "文件",
  patch: "补丁",
  text: "文本",
  image: "图片",
};

export function resolveWorkspaceTabDisplay(tab: WorkspaceTab): WorkspaceTabDisplay {
  const key = workspaceTabKey(tab);

  if (isAgentDirectoryTab(tab)) {
    return {
      key,
      title: "子智能体目录",
      icon: {
        kind: "glyph",
        glyph: "list-tree",
      },
      typeHint: "目录",
      diffBadge: "",
    };
  }

  if (isAgentTab(tab)) {
    return {
      key,
      title: tab.title || "子智能体",
      icon: {
        kind: "glyph",
        glyph: "bot",
      },
      hint: tab.subagentType,
      typeHint: "子智能体",
      diffBadge: "",
    };
  }

  if (isSourceTab(tab)) {
    const meta = sourceTabMeta(tab.source);
    // 图标按**文件类型**取（patch 是 `foo.ts` 的补丁，就还显示 ts 的角标），
    // 变更量单独走 diffBadge —— 对齐 ZCode `SidePaneTabTrigger.tsx:250-254`。
    const kind = resolveFileKind(undefined, meta.title);
    const stats =
      tab.source.type === "patch" ? patchDiffStats(tab.source.patch) : null;
    return {
      key,
      title: meta.title,
      icon: {
        kind: "badge",
        badge: fileKindBadge(kind),
      },
      hint: meta.subtitle,
      typeHint: SOURCE_TYPE_LABEL[tab.source.type] || "文件",
      diffBadge: stats ? formatDiffStats(stats) : "",
    };
  }

  // 任务预览 tab：它没有文件身份，**不能**落到下面的文件分支被当成无扩展名文件。
  // 图标用 activity 字形（执行过程），工具名进 hint 供浮层搜索。
  if (isTaskTab(tab)) {
    return {
      key,
      title: tab.title || "任务预览",
      icon: {
        kind: "glyph",
        glyph: "activity",
      },
      hint: String(resolveTaskResultMap(tab.fallbackTask).toolName || "") || undefined,
      typeHint: "任务",
      diffBadge: "",
    };
  }

  const file = tab as WorkspaceFileItem;
  const kind = resolveFileKind(file.type, file.name);
  return {
    key,
    title: file.name || "未命名文件",
    icon: {
      kind: "badge",
      badge: fileKindBadge(kind)
    },
    hint: file.relativePath || file.originFileName,
    typeHint: (file.type || file.name?.split(".").pop() || "").toLowerCase(),
    diffBadge: "",
  };
}
