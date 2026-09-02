import { basenameOfPath, getTaskFiles, normalizeRelativePath } from "@/utils/taskArtifacts";
import { buildFilePreviewUrlForBrowser } from "@/utils/fileUrl";

const ARTIFACT_KEY_SPLIT_PATTERN = /[、,\r\n，]+/;

function asText(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function resolveSummaryRecords(task?: CHAT.Task) {
  const taskRecord = (task || {}) as unknown as Record<string, unknown>;
  const resultMapRecord = (task?.resultMap || {}) as Record<string, unknown>;
  return { taskRecord, resultMapRecord };
}

function collectSummaryProtocolTexts(task?: CHAT.Task): string[] {
  if (!task) {
    return [];
  }
  const { taskRecord, resultMapRecord } = resolveSummaryRecords(task);
  return [
    asText(resultMapRecord.taskSummary),
    asText(taskRecord.taskSummary),
    asText(task.result),
    asText(resultMapRecord.result),
  ].filter(Boolean);
}

function resolveTaskSummarySourceText(task?: CHAT.Task) {
  return collectSummaryProtocolTexts(task)[0] || "";
}

/**
 * 时间线任务总结和最终结论共用同一套文本回退顺序，
 * 这样可以避免不同展示区各自兜底时出现文案不一致。
 */
export function resolveTaskSummaryText(task?: CHAT.Task) {
  const rawText = resolveTaskSummarySourceText(task);
  const delimiterIndex = rawText.indexOf("$$$");
  return delimiterIndex >= 0
    ? rawText.slice(0, delimiterIndex).trim()
    : rawText;
}

function parseArtifactKeys(value: unknown): string[] {
  if (typeof value === "string") {
    return value
      .split(ARTIFACT_KEY_SPLIT_PATTERN)
      .map((item) => normalizeArtifactKey(item))
      .filter(Boolean);
  }
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => normalizeArtifactKey(typeof item === "string" ? item : ""))
    .filter(Boolean);
}

function readStoredArtifactKeys(task?: CHAT.Task): string[] {
  if (!task) {
    return [];
  }
  const { taskRecord, resultMapRecord } = resolveSummaryRecords(task);
  const fromMap = parseArtifactKeys(resultMapRecord.artifactKeys);
  if (fromMap.length) {
    return fromMap;
  }
  return parseArtifactKeys(taskRecord.artifactKeys);
}

function normalizeArtifactKey(raw: string) {
  let text = raw.trim();
  if (!text) {
    return "";
  }
  text = text.replace(/^[-*+]\s+/, "").replace(/^\d+[.)]\s+/, "").trim();
  text = text.replace(/^[*`]+|[`*]+$/g, "").trim();
  const markdownLink = text.match(/^\[[^\]]*]\(([^)]+)\)$/);
  if (markdownLink?.[1]) {
    text = markdownLink[1].trim();
  }
  text = text.replace(/^['"]+|['"]+$/g, "").trim();
  return text.replace(/[。.;]+$/g, "").trim();
}

function parseProtocolArtifactKeys(text: string): string[] {
  const delimiterIndex = text.indexOf("$$$");
  if (delimiterIndex < 0) {
    return [];
  }
  return text
    .slice(delimiterIndex + 3)
    .trim()
    .split(ARTIFACT_KEY_SPLIT_PATTERN)
    .map((item) => normalizeArtifactKey(item))
    .filter(Boolean);
}

export function resolveTaskSummaryArtifactKeys(task?: CHAT.Task): string[] {
  for (const text of collectSummaryProtocolTexts(task)) {
    const keys = parseProtocolArtifactKeys(text);
    if (keys.length) {
      return keys;
    }
  }
  return readStoredArtifactKeys(task);
}

function workspacePathOf(file: CHAT.TFile) {
  return normalizeRelativePath(
    file.relativePath || file.originFileName || file.name
  ).toLowerCase();
}

function pickFileByWorkspacePath(files: CHAT.TFile[], key: string, used: Set<number>) {
  const needle = normalizeRelativePath(key).toLowerCase();
  if (!needle) {
    return -1;
  }
  const exact = files.findIndex(
    (file, index) => !used.has(index) && workspacePathOf(file) === needle
  );
  if (exact >= 0) {
    return exact;
  }
  const base = basenameOfPath(needle).toLowerCase();
  const matches = files
    .map((file, index) => ({ file, index }))
    .filter(
      ({ file, index }) =>
        !used.has(index) && basenameOfPath(workspacePathOf(file)) === base
    );
  return matches.length === 1 ? matches[0].index : -1;
}

function resolveSessionId(task?: CHAT.Task): string {
  const { taskRecord, resultMapRecord } = resolveSummaryRecords(task);
  return (
    asText(resultMapRecord.sessionId) ||
    asText(taskRecord.sessionId) ||
    asText(resultMapRecord.requestId) ||
    asText(taskRecord.requestId)
  );
}

function fileFromArtifactKey(key: string, sessionId?: string): CHAT.TFile | null {
  const path = normalizeRelativePath(key);
  if (!path) {
    return null;
  }
  const name = basenameOfPath(path) || path;
  const previewUrl = buildFilePreviewUrlForBrowser(sessionId, path);
  return {
    name,
    url: previewUrl,
    type: name.includes(".") ? name.split(".").pop() || "" : "",
    size: 0,
    downloadUrl: previewUrl || undefined,
    relativePath: path,
    originFileName: path,
  };
}

export function isSameDeliveryFile(left: CHAT.TFile, right: CHAT.TFile) {
  const leftKey = String(left.resourceKey || "").toLowerCase();
  const rightKey = String(right.resourceKey || "").toLowerCase();
  if (leftKey && rightKey && leftKey === rightKey) {
    return true;
  }
  const leftPath = String(
    left.relativePath || left.originFileName || left.name || ""
  )
    .replace(/\\/g, "/")
    .toLowerCase();
  const rightPath = String(
    right.relativePath || right.originFileName || right.name || ""
  )
    .replace(/\\/g, "/")
    .toLowerCase();
  if (leftPath && rightPath && leftPath === rightPath) {
    return true;
  }
  if (left.url && right.url && left.url === right.url) {
    return true;
  }
  if (left.downloadUrl && right.downloadUrl && left.downloadUrl === right.downloadUrl) {
    return true;
  }
  return false;
}

function hasBasenameInPool(files: CHAT.TFile[], key: string) {
  const base = basenameOfPath(normalizeRelativePath(key)).toLowerCase();
  if (!base) {
    return false;
  }
  return files.some((file) => basenameOfPath(workspacePathOf(file)) === base);
}

function pickFilesByArtifactKeys(
  files: CHAT.TFile[],
  keys: string[],
  sessionId?: string
) {
  const used = new Set<number>();
  const featured: CHAT.TFile[] = [];
  for (const key of keys) {
    const index = pickFileByWorkspacePath(files, key, used);
    if (index >= 0) {
      used.add(index);
      featured.push(files[index]);
      continue;
    }
    if (hasBasenameInPool(files, key)) {
      continue;
    }
    const synthesized = fileFromArtifactKey(key, sessionId);
    if (synthesized) {
      featured.push(synthesized);
    }
  }
  return featured;
}

function mergeDeliveryFiles(task?: CHAT.Task, sessionFiles?: CHAT.TFile[]) {
  const merged: CHAT.TFile[] = [];
  for (const file of [...getTaskFiles(task), ...(sessionFiles || [])]) {
    if (!merged.some((item) => isSameDeliveryFile(item, file))) {
      merged.push(file);
    }
  }
  return merged;
}

export function pickFeaturedDeliveryFiles(
  task?: CHAT.Task,
  sessionFiles?: CHAT.TFile[],
  sessionId?: string
): CHAT.TFile[] {
  const keys = resolveTaskSummaryArtifactKeys(task);
  if (!keys.length) {
    return [];
  }
  return pickFilesByArtifactKeys(
    mergeDeliveryFiles(task, sessionFiles),
    keys,
    sessionId || resolveSessionId(task)
  );
}

export function shouldShowWorkspaceFilesEntry(
  featured: CHAT.TFile[],
  task?: CHAT.Task,
  sessionFiles?: CHAT.TFile[]
) {
  const candidates = [...getTaskFiles(task), ...(sessionFiles || [])];
  return candidates.some(
    (candidate) =>
      !featured.some((file) => isSameDeliveryFile(file, candidate))
  );
}
