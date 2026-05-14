import type { KnowledgeBase, KnowledgeBaseFile } from "./types";

export function resolveSelectedKnowledgeBaseId(
  knowledgeBases: Array<Pick<KnowledgeBase, "id">>,
  currentKnowledgeBaseId: string,
  preferredKnowledgeBaseId?: string
) {
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
  return (
    Boolean(currentToolBaseUrl) &&
    lastBootstrappedToolBaseUrl !== currentToolBaseUrl
  );
}

export function shouldPollKnowledgeBaseFiles(
  files: Array<Pick<KnowledgeBaseFile, "fileStatus">>
) {
  return files.some(
    (file) => file.fileStatus === "PENDING" || file.fileStatus === "RUNNING"
  );
}

export function resolveKnowledgeBaseAfterDeletion(
  knowledgeBases: Array<Pick<KnowledgeBase, "id">>,
  currentKnowledgeBaseId: string,
  deletedKnowledgeBaseId: string
) {
  const availableKnowledgeBases = knowledgeBases.filter(
    (item) => item.id !== deletedKnowledgeBaseId
  );

  return resolveSelectedKnowledgeBaseId(
    availableKnowledgeBases,
    currentKnowledgeBaseId === deletedKnowledgeBaseId ? "" : currentKnowledgeBaseId
  );
}
