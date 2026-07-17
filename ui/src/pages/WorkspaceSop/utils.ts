import { buildDefaultToolBaseUrl } from "@/pages/WorkspaceImageGeneration/utils";

import type { SopEditorDraft, SopItem, SopStatus, SopStep } from "./types";

export const SOP_WORKSPACE_STORAGE_KEY = "workspace-sop:state";

export function resolveToolBaseUrl(): string {
  return buildDefaultToolBaseUrl();
}

export function createEmptyStep(): SopStep {
  return { title: "", steps: [""] };
}

export function createEmptyDraft(): SopEditorDraft {
  return {
    sopId: null,
    sopName: "",
    sopDesc: "",
    sopType: "list",
    sopSteps: [createEmptyStep()],
    status: "online",
  };
}

export function sopItemToDraft(item: SopItem): SopEditorDraft {
  return {
    sopId: item.sopId,
    sopName: item.sopName,
    sopDesc: item.sopDesc,
    sopType: item.sopType || "list",
    sopSteps:
      item.sopSteps && item.sopSteps.length > 0
        ? item.sopSteps.map((step) => ({
            title: step.title || "",
            steps: step.steps && step.steps.length > 0 ? [...step.steps] : [""],
          }))
        : [createEmptyStep()],
    status: item.status || "online",
  };
}

export function normalizeStatus(value: unknown): SopStatus {
  const status = String(value || "online").toLowerCase();
  if (status === "offline" || status === "draft") {
    return status;
  }
  return "online";
}

export function statusLabel(status: SopStatus): string {
  if (status === "online") return "已上线";
  if (status === "offline") return "已下线";
  return "草稿";
}
