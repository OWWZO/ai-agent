import { getDeviceId } from "@/services/agentConversation";
import { resolveServiceBaseUrl } from "@/utils/origin";

export type GenUiExportFormat = "pdf" | "docx";
export type GenUiExportMode = "document" | "deck";

function resolveBaseUrl() {
  // SERVICE_BASE_URL is injected by vite define / env in this project.
  const configured =
    typeof SERVICE_BASE_URL === "string" ? SERVICE_BASE_URL : "";
  return resolveServiceBaseUrl(configured);
}

/**
 * Export GenUI tree via backend API and trigger browser download.
 */
export async function exportGenUiFile(options: {
  format: GenUiExportFormat;
  tree: unknown;
  mode?: GenUiExportMode;
  filename?: string;
}): Promise<void> {
  const { format, tree, mode = "document", filename } = options;
  if (!tree) {
    throw new Error("缺少 GenUI tree");
  }

  const base = resolveBaseUrl();
  const url = `${base}/api/agent/genui/export/${format}`;
  const response = await fetch(url, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-Device-Id": getDeviceId(),
    },
    body: JSON.stringify({ tree, mode }),
  });

  if (!response.ok) {
    let message = `导出失败 (${response.status})`;
    try {
      const err = await response.json();
      if (err?.info || err?.msg) message = err.info || err.msg;
    } catch {
      // ignore
    }
    throw new Error(message);
  }

  const blob = await response.blob();
  const stamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
  const defaultName = filename || `genui-${stamp}.${format}`;
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = objectUrl;
  a.download = defaultName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}
