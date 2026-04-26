import dayjs from "dayjs";

import type {
  KnowledgeBaseFile,
  MRagFileStatus,
  MRagWorkspaceStoredState,
} from "./types";
import {
  buildDefaultToolBaseUrl,
  formatBytes,
  toPrettyJson,
  trimTrailingSlash,
} from "@/pages/WorkspaceImageGeneration/utils";

export { formatBytes, toPrettyJson };

export const MRAG_WORKSPACE_STORAGE_KEY = "workspace-mrag:state";
export const MRAG_FILE_POLL_INTERVAL_MS = 2000;
export const MRAG_FILE_REFRESH_DELAY_MS = 1000;

type FileStatusMeta = {
  label: string;
  className: string;
};

export function createDefaultMRagWorkspaceStoredState(): MRagWorkspaceStoredState {
  if (typeof window === "undefined") {
    return {
      toolBaseUrl: "http://127.0.0.1:1601",
      selectedKnowledgeBaseId: "",
    };
  }

  return {
    toolBaseUrl: buildDefaultToolBaseUrl(),
    selectedKnowledgeBaseId: "",
  };
}

export function parseMRagWorkspaceStoredState(
  rawValue?: string | null
): MRagWorkspaceStoredState {
  const defaults = createDefaultMRagWorkspaceStoredState();
  if (!rawValue) {
    return defaults;
  }

  try {
    const parsed = JSON.parse(rawValue) as Partial<MRagWorkspaceStoredState>;
    return {
      toolBaseUrl: trimTrailingSlash(parsed.toolBaseUrl || defaults.toolBaseUrl),
      selectedKnowledgeBaseId: parsed.selectedKnowledgeBaseId || "",
    };
  } catch {
    return defaults;
  }
}

export function loadMRagWorkspaceStoredState(): MRagWorkspaceStoredState {
  if (typeof window === "undefined") {
    return createDefaultMRagWorkspaceStoredState();
  }

  return parseMRagWorkspaceStoredState(
    window.localStorage.getItem(MRAG_WORKSPACE_STORAGE_KEY)
  );
}

export function persistMRagWorkspaceStoredState(
  state: MRagWorkspaceStoredState
): void {
  if (typeof window === "undefined") {
    return;
  }

  const payload: MRagWorkspaceStoredState = {
    toolBaseUrl: trimTrailingSlash(state.toolBaseUrl),
    selectedKnowledgeBaseId: state.selectedKnowledgeBaseId || "",
  };
  window.localStorage.setItem(MRAG_WORKSPACE_STORAGE_KEY, JSON.stringify(payload));
}

export function formatWorkspaceDateTime(
  value?: string | number | null
): string {
  if (!value) {
    return "暂无";
  }

  const formatted = dayjs(value);
  if (!formatted.isValid()) {
    return "暂无";
  }
  return formatted.format("YYYY-MM-DD HH:mm");
}

export function resolveFileStatusMeta(status: MRagFileStatus): FileStatusMeta {
  switch (status) {
    case "SUCCESS":
      return {
        label: "已完成",
        className: "border-emerald-200 bg-emerald-50 text-emerald-700",
      };
    case "FAILED":
      return {
        label: "失败",
        className: "border-rose-200 bg-rose-50 text-rose-700",
      };
    case "RUNNING":
      return {
        label: "处理中",
        className: "border-sky-200 bg-sky-50 text-sky-700",
      };
    case "PENDING":
      return {
        label: "排队中",
        className: "border-amber-200 bg-amber-50 text-amber-700",
      };
    default:
      return {
        label: "未知",
        className: "border-slate-200 bg-slate-100 text-slate-600",
      };
  }
}

export function formatFileDocCount(
  file: Pick<KnowledgeBaseFile, "docCount" | "fileStatus">
): string {
  if (file.fileStatus === "SUCCESS") {
    return `${file.docCount} 个片段`;
  }
  if (file.fileStatus === "FAILED") {
    return "未完成切片";
  }
  return "处理中";
}

export function resolveSourceSummary(url: string): string {
  if (!url) {
    return "暂无来源";
  }

  try {
    const parsed = new URL(url);
    return parsed.hostname || url;
  } catch {
    return url;
  }
}
