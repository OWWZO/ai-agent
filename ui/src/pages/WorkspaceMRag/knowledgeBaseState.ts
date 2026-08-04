import type { KnowledgeBase, KnowledgeBaseFile } from "./types";

export function resolveSelectedKnowledgeBaseId(
  knowledgeBases: Array<Pick<KnowledgeBase, "id">>,
  currentKnowledgeBaseId: string,
  preferredKnowledgeBaseId?: string
) {
  // 选择优先级为外部偏好、当前会话、首个可用知识库，避免刷新后选中已删除 ID。
  const preferred = preferredKnowledgeBaseId?.trim();
  if (preferred && knowledgeBases.some((item) => item.id === preferred)) {
    return preferred;
  }
  if (
    currentKnowledgeBaseId &&
    knowledgeBases.some((item) => item.id === currentKnowledgeBaseId)
  ) {
    return currentKnowledgeBaseId;
  }
  return knowledgeBases[0]?.id || "";
}

export function shouldBootstrapKnowledgeBases(
  lastBootstrappedToolBaseUrl: string | null,
  currentToolBaseUrl: string
) {
  // 工具地址变化意味着后端实例可能不同，需要重新执行知识库初始化。
  return (
    Boolean(currentToolBaseUrl) &&
    lastBootstrappedToolBaseUrl !== currentToolBaseUrl
  );
}

export function shouldPollKnowledgeBaseFiles(
  files: Array<Pick<KnowledgeBaseFile, "fileStatus">>
) {
  // 只有 PENDING/RUNNING 代表仍在处理，失败或完成状态都应停止轮询。
  return files.some(
    (file) => file.fileStatus === "PENDING" || file.fileStatus === "RUNNING"
  );
}

export function resolveKnowledgeBaseAfterDeletion(
  knowledgeBases: Array<Pick<KnowledgeBase, "id">>,
  currentKnowledgeBaseId: string,
  deletedKnowledgeBaseId: string
) {
  // 删除当前知识库时清空当前 ID，再复用统一选择规则落到剩余列表。
  const availableKnowledgeBases = knowledgeBases.filter(
    (item) => item.id !== deletedKnowledgeBaseId
  );

  return resolveSelectedKnowledgeBaseId(
    availableKnowledgeBases,
    currentKnowledgeBaseId === deletedKnowledgeBaseId ? "" : currentKnowledgeBaseId
  );
}
