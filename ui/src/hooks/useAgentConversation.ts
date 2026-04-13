import { useCallback, useEffect, useRef, useState } from "react";
import {
  conversationApi,
  roleLibraryApi,
  type ConversationDetail,
  type ConversationListItem,
  type FixRoleItem,
} from "@/services/agentConversation";
import {
  buildConversationFromDetail,
  createConversation,
  pruneHistory,
} from "@/utils/chatHistory";

const removeConversationByKey = (
  conversations: CHAT.ConversationHistory[],
  key: string
) => {
  return conversations.filter(
    (conversation) => conversation.sessionId !== key && conversation.id !== key
  );
};

/**
 * 对话历史管理 Hook
 *
 * 状态拆分：
 * - remoteConversations: 服务端返回的轻量摘要列表
 * - detailCache: 已经懒加载过的会话详情缓存
 * - draftConversations: 本地新建草稿和流式中的运行态会话
 */
export function useAgentConversation() {
  const [apiMode, setApiMode] = useState(false);
  const [apiLoading, setApiLoading] = useState(false);
  const apiChecked = useRef(false);

  const [remoteConversations, setRemoteConversations] = useState<
    ConversationListItem[]
  >([]);
  const [remoteTotal, setRemoteTotal] = useState(0);
  const [fixRoles, setFixRoles] = useState<FixRoleItem[]>([]);
  const [detailCache, setDetailCache] = useState<CHAT.ConversationDetailCache>(
    {}
  );
  const [draftConversations, setDraftConversations] = useState<
    CHAT.ConversationHistory[]
  >([]);

  const detailCacheRef = useRef(detailCache);
  const draftConversationsRef = useRef(draftConversations);

  useEffect(() => {
    detailCacheRef.current = detailCache;
  }, [detailCache]);

  useEffect(() => {
    draftConversationsRef.current = draftConversations;
  }, [draftConversations]);

  const loadRoleLibrary = useCallback(async () => {
    try {
      const data: any = await roleLibraryApi.list();
      setFixRoles(data || []);
    } catch (e) {
      console.error("加载角色库失败", e);
    }
  }, []);

  const loadRemoteConversations = useCallback(
    async (pageNo = 1, pageSize = 50) => {
      try {
        setApiLoading(true);
        const data: any = await conversationApi.list(pageNo, pageSize);
        if (data) {
          setRemoteConversations(data.list || []);
          setRemoteTotal(data.total || 0);
        }
      } catch (e) {
        console.error("加载会话列表失败", e);
      } finally {
        setApiLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    if (apiChecked.current) return;
    apiChecked.current = true;

    conversationApi
      .list(1, 1)
      .then((resp: any) => {
        if (resp && resp.list !== undefined) {
          setApiMode(true);
          loadRemoteConversations();
          loadRoleLibrary();
        }
      })
      .catch(() => {
        console.log("API不可用，无法加载服务端历史记录");
      });
  }, [loadRemoteConversations, loadRoleLibrary]);

  const cacheConversationDetail = useCallback(
    (conversation: CHAT.ConversationHistory) => {
      setDetailCache((prev) => ({
        ...prev,
        [conversation.sessionId]: conversation,
      }));
      return conversation;
    },
    []
  );

  const removeConversationDetail = useCallback((sessionId: string) => {
    setDetailCache((prev) => {
      if (!prev[sessionId]) {
        return prev;
      }

      const next = { ...prev };
      delete next[sessionId];
      return next;
    });
  }, []);

  const upsertDraftConversation = useCallback(
    (conversation: CHAT.ConversationHistory) => {
      setDraftConversations((prev) =>
        pruneHistory([
          conversation,
          ...removeConversationByKey(prev, conversation.sessionId),
        ])
      );
      return conversation;
    },
    []
  );

  const removeDraftConversation = useCallback((key: string) => {
    setDraftConversations((prev) => removeConversationByKey(prev, key));
  }, []);

  const createDraftConversation = useCallback(
    (partial: Partial<CHAT.ConversationHistory> = {}) => {
      const nextConversation = createConversation(partial);
      setDraftConversations((prev) =>
        pruneHistory([
          nextConversation,
          ...removeConversationByKey(prev, nextConversation.sessionId),
        ])
      );
      return nextConversation;
    },
    []
  );

  const loadConversationDetail = useCallback(
    async (
      sessionId: string,
      force = false
    ): Promise<CHAT.ConversationHistory | null> => {
      if (!force && detailCacheRef.current[sessionId]) {
        return detailCacheRef.current[sessionId];
      }

      try {
        const data: any = await conversationApi.detail(sessionId);
        if (data?.conversation) {
          // 详情缓存只保存服务端 turn/event 还原后的结果，
          // 草稿态在首次加载详情后主动让位，避免重新引入双持久化真相源。
          const cachedConversation =
            detailCacheRef.current[sessionId] ||
            draftConversationsRef.current.find(
              (conversation) => conversation.sessionId === sessionId
            ) ||
            null;
          const hydratedConversation = buildConversationFromDetail(
            data as ConversationDetail,
            cachedConversation
          );

          cacheConversationDetail(hydratedConversation);
          removeDraftConversation(sessionId);
          return hydratedConversation;
        }
      } catch (e) {
        console.error("加载会话详情失败", e);
      }
      return null;
    },
    [cacheConversationDetail, removeDraftConversation]
  );

  const createRemoteConversation = useCallback(
    async (
      sessionId: string,
      agentType: number,
      productType: string,
      title?: string,
      aiAgentId?: string
    ) => {
      try {
        await conversationApi.create({
          sessionId,
          agentType,
          productType,
          title,
          aiAgentId,
        });
        loadRemoteConversations();
      } catch (e) {
        console.error("创建远程会话失败", e);
      }
    },
    [loadRemoteConversations]
  );

  const deleteRemoteConversation = useCallback(
    async (sessionId: string) => {
      try {
        await conversationApi.delete(sessionId);
        removeConversationDetail(sessionId);
        removeDraftConversation(sessionId);
        loadRemoteConversations();
      } catch (e) {
        console.error("删除远程会话失败", e);
      }
    },
    [loadRemoteConversations, removeConversationDetail, removeDraftConversation]
  );

  const renameRemoteConversation = useCallback(
    async (sessionId: string, title: string) => {
      try {
        await conversationApi.rename(sessionId, title);
        setDetailCache((prev) => {
          if (!prev[sessionId]) {
            return prev;
          }

          return {
            ...prev,
            [sessionId]: {
              ...prev[sessionId],
              title,
              chatTitle: prev[sessionId].chatTitle || title,
              updatedAt: Date.now(),
            },
          };
        });
        setDraftConversations((prev) =>
          prev.map((conversation) =>
            conversation.sessionId === sessionId
              ? {
                ...conversation,
                title,
                chatTitle: conversation.chatTitle || title,
                updatedAt: Date.now(),
              }
              : conversation
          )
        );
        loadRemoteConversations();
      } catch (e) {
        console.error("重命名失败", e);
      }
    },
    [loadRemoteConversations]
  );

  return {
    apiMode,
    apiLoading,
    remoteConversations,
    remoteTotal,
    fixRoles,
    detailCache,
    draftConversations,
    loadRemoteConversations,
    loadRoleLibrary,
    loadConversationDetail,
    cacheConversationDetail,
    removeConversationDetail,
    createDraftConversation,
    upsertDraftConversation,
    removeDraftConversation,
    createRemoteConversation,
    deleteRemoteConversation,
    renameRemoteConversation,
  };
}
