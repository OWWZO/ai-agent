import { useCallback, useEffect, useRef, useState } from "react";
import {
  conversationApi,
  restoreMessages,
  type ConversationListItem,
} from "@/services/agentConversation";

/**
 * 对话历史管理Hook
 * 仅使用服务端会话接口管理历史记录
 *
 * 策略：
 * - 优先从API加载会话列表
 * - 会话详情从API懒加载
 * - 流式对话仍走现有SSE逻辑，服务端自动持久化
 */
export function useAgentConversation() {
  // 是否使用API模式 (检测后端是否可用)
  const [apiMode, setApiMode] = useState(false);
  const [apiLoading, setApiLoading] = useState(false);
  const apiChecked = useRef(false);

  // 远程会话列表 (轻量元数据)
  const [remoteConversations, setRemoteConversations] = useState<ConversationListItem[]>([]);
  const [remoteTotal, setRemoteTotal] = useState(0);

  // 检测API是否可用
  useEffect(() => {
    if (apiChecked.current) return;
    apiChecked.current = true;

    conversationApi
      .list(1, 1)
      .then((resp: any) => {
        // 拦截器已解包: resp 直接是 {total, list}
        if (resp && resp.list !== undefined) {
          setApiMode(true);
          loadRemoteConversations();
        }
      })
      .catch(() => {
        console.log("API不可用，无法加载服务端历史记录");
      });
  }, []);

  /**
   * 从API加载会话列表
   */
  const loadRemoteConversations = useCallback(async (pageNo = 1, pageSize = 50) => {
    try {
      setApiLoading(true);
      const data: any = await conversationApi.list(pageNo, pageSize);
      // 拦截器已解包: data 直接是 {total, list}
      if (data) {
        setRemoteConversations(data.list || []);
        setRemoteTotal(data.total || 0);
      }
    } catch (e) {
      console.error("加载会话列表失败", e);
    } finally {
      setApiLoading(false);
    }
  }, []);

  /**
   * 从API加载会话详情，返回ChatItem[]
   */
  const loadConversationDetail = useCallback(async (sessionId: string): Promise<CHAT.ChatItem[]> => {
    try {
      const data: any = await conversationApi.detail(sessionId);
      // 拦截器已解包: data 直接是 {conversation, messages}
      if (data?.messages) {
        return restoreMessages(data.messages);
      }
    } catch (e) {
      console.error("加载会话详情失败", e);
    }
    return [];
  }, []);

  /**
   * 创建远程会话
   */
  const createRemoteConversation = useCallback(
    async (sessionId: string, agentType: number, productType: string, title?: string) => {
      try {
        await conversationApi.create({ sessionId, agentType, productType, title });
        // 刷新列表
        loadRemoteConversations();
      } catch (e) {
        console.error("创建远程会话失败", e);
      }
    },
    [loadRemoteConversations]
  );

  /**
   * 删除远程会话
   */
  const deleteRemoteConversation = useCallback(
    async (sessionId: string) => {
      try {
        await conversationApi.delete(sessionId);
        loadRemoteConversations();
      } catch (e) {
        console.error("删除远程会话失败", e);
      }
    },
    [loadRemoteConversations]
  );

  /**
   * 重命名远程会话
   */
  const renameRemoteConversation = useCallback(
    async (sessionId: string, title: string) => {
      try {
        await conversationApi.rename(sessionId, title);
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
    loadRemoteConversations,
    loadConversationDetail,
    createRemoteConversation,
    deleteRemoteConversation,
    renameRemoteConversation,
  };
}
