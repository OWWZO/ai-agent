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
import { normalizeToolBaseUrlForBrowser } from "@/utils/fileUrl";

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
      toolBaseUrl: "/tool",
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
      toolBaseUrl: normalizeToolBaseUrlForBrowser(
        trimTrailingSlash(parsed.toolBaseUrl || defaults.toolBaseUrl)
      ),
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
        className: "bg-[var(--status-success-bg)] text-[var(--status-success-text)]",
      };
    case "FAILED":
      return {
        label: "失败",
        className: "bg-[var(--status-failed-bg)] text-[var(--status-failed-text)]",
      };
    case "RUNNING":
      return {
        label: "处理中",
        className: "bg-[var(--status-running-bg)] text-[var(--status-running-text)]",
      };
    case "PENDING":
      return {
        label: "排队中",
        className: "bg-[var(--status-pending-bg)] text-[var(--status-pending-text)]",
      };
    default:
      return {
        label: "未知",
        className: "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]",
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
