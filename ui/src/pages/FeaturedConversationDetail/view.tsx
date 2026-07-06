import { Empty, Spin } from "antd";
import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

import ChatView from "@/components/ChatView";
import { ROUTES } from "@/router/routes";
import type { FeaturedConversationDetail } from "@/services/featuredConversation";
import {
  hydrateConversationFromReplayFrames,
  isHistoryDetailEmpty,
} from "@/utils/conversationHistory";

type FeaturedConversationDetailViewProps = {
  loading: boolean;
  detail: FeaturedConversationDetail | null;
};

function resolveUnavailableReason(detail: FeaturedConversationDetail) {
  if (detail.contentUnavailableReason) {
    return detail.contentUnavailableReason;
  }
  return "session_history_empty";
}

export function FeaturedConversationDetailView(
  props: FeaturedConversationDetailViewProps
) {
  const conversation =
    props.detail?.historyDetail &&
    !isHistoryDetailEmpty(props.detail.historyDetail)
      ? hydrateConversationFromReplayFrames(props.detail.historyDetail)
      : null;
  const hasRenderableContent = Boolean(
    conversation &&
      (conversation.chatList.length > 0 || conversation.dataChatList.length > 0)
  );

  if (props.loading) {
    return (
      <div className="flex h-full w-full items-center justify-center px-6">
        <Spin size="large" />
      </div>
    );
  }

  if (!props.detail) {
    return (
      <div className="flex h-full w-full items-center justify-center px-6">
        <Empty description="未找到对应的精品对话" />
      </div>
    );
  }

  const detail = props.detail;

  return (
    <div className="flex h-full w-full flex-col overflow-hidden px-3 py-3 md:px-4 md:py-4">
      <div className="mb-3 shrink-0">
        <Link
          to={ROUTES.FEATURED_CONVERSATIONS}
          className="inline-flex items-center gap-2 text-[13px] font-medium text-[var(--chat-text-soft)] transition-colors hover:text-[var(--chat-text)]"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>返回精品列表</span>
        </Link>
      </div>

      {!detail.contentAvailable || !hasRenderableContent ? (
        <div className="min-h-0 flex-1 rounded-[24px] border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/72 p-6 md:p-8">
          <div className="mx-auto max-w-[960px]">
            <div className="text-[18px] font-medium text-[var(--chat-text)]">
              正文暂不可用
            </div>
            <p className="mt-3 text-[14px] leading-7 text-[var(--chat-text-soft)]">
              {resolveUnavailableReason(detail)}
            </p>
          </div>
        </div>
      ) : (
        <div className="min-h-0 flex-1 overflow-hidden">
          {/* 精品详情直接复用真实历史会话视图，只关闭输入能力，并尽量把空间留给对话正文。 */}
          <ChatView
            inputInfo={{
              message: "",
              deepThink: conversation?.deepThink ?? false,
              outputStyle: conversation?.productType,
            }}
            product={undefined}
            conversation={conversation!}
            chatRoles={[]}
            readOnly
            onConversationChange={() => {}}
            onRoleSelect={() => {}}
          />
        </div>
      )}
    </div>
  );
}
