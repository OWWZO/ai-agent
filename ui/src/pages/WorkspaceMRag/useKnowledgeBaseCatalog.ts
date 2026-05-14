import { useCallback, useState } from "react";

import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  mapMragError,
} from "@/services/mragWorkspace";
import { showMessage } from "@/utils";
import type { KnowledgeBase } from "./types";

type RefreshKnowledgeBaseOptions = {
  silent?: boolean;
};

export function useKnowledgeBaseCatalog(toolBaseUrl: string) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [knowledgeBasesLoading, setKnowledgeBasesLoading] = useState(false);
  const [knowledgeBasesError, setKnowledgeBasesError] = useState("");
  const [createKnowledgeBaseName, setCreateKnowledgeBaseName] = useState("");
  const [createKnowledgeBaseDesc, setCreateKnowledgeBaseDesc] = useState("");
  const [creatingKnowledgeBase, setCreatingKnowledgeBase] = useState(false);
  const [deletingKnowledgeBaseId, setDeletingKnowledgeBaseId] = useState("");

  const refreshKnowledgeBases = useCallback(
    async (options?: RefreshKnowledgeBaseOptions) => {
      if (!options?.silent) {
        setKnowledgeBasesLoading(true);
      }

      try {
        const nextKnowledgeBases = await listKnowledgeBases(toolBaseUrl);
        setKnowledgeBases(nextKnowledgeBases);
        setKnowledgeBasesError("");
        return nextKnowledgeBases;
      } catch (error) {
        setKnowledgeBasesError(mapMragError(error));
        setKnowledgeBases([]);
        return [];
      } finally {
        setKnowledgeBasesLoading(false);
      }
    },
    [toolBaseUrl]
  );

  const handleCreateKnowledgeBase = useCallback(async () => {
    const kbName = createKnowledgeBaseName.trim();
    if (!kbName) {
      showMessage()?.error("请输入知识库名称");
      return null;
    }

    setCreatingKnowledgeBase(true);
    try {
      const createdKnowledgeBase = await createKnowledgeBase(toolBaseUrl, {
        kbName,
        kbDesc: createKnowledgeBaseDesc.trim(),
      });
      setCreateKnowledgeBaseName("");
      setCreateKnowledgeBaseDesc("");
      showMessage()?.success("知识库已创建");
      return createdKnowledgeBase;
    } catch (error) {
      showMessage()?.error(mapMragError(error));
      return null;
    } finally {
      setCreatingKnowledgeBase(false);
    }
  }, [createKnowledgeBaseDesc, createKnowledgeBaseName, toolBaseUrl]);

  const deleteKnowledgeBaseById = useCallback(
    async (kbId: string) => {
      if (!kbId) {
        return null;
      }

      setDeletingKnowledgeBaseId(kbId);
      try {
        const deletedResult = await deleteKnowledgeBase(toolBaseUrl, kbId);
        return deletedResult;
      } catch (error) {
        showMessage()?.error(mapMragError(error));
        return null;
      } finally {
        setDeletingKnowledgeBaseId("");
      }
    },
    [toolBaseUrl]
  );

  return {
    knowledgeBases,
    knowledgeBasesLoading,
    knowledgeBasesError,
    createKnowledgeBaseName,
    createKnowledgeBaseDesc,
    creatingKnowledgeBase,
    deletingKnowledgeBaseId,
    setCreateKnowledgeBaseName,
    setCreateKnowledgeBaseDesc,
    refreshKnowledgeBases,
    handleCreateKnowledgeBase,
    deleteKnowledgeBaseById,
  };
}
