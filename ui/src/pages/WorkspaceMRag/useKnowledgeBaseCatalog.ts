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
      // silent 刷新保留页面交互，非 silent 刷新才显示全局 loading；失败时清空旧列表避免误操作。
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
    // 名称在请求前 trim，创建成功后清空草稿但把新对象交给页面决定是否选中。
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
      // 删除状态只记录当前 id，使列表中的其它操作仍能保持可用。
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
