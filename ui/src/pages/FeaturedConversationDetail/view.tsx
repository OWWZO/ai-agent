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
  embedded?: boolean;
  loading: boolean;
  detail: FeaturedConversationDetail | null;
  onBack?: () => void;
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
      <div className="flex h-full w-full items-center justify-center bg-[var(--chat-bg)] px-6">
        <Spin size="large" />
      </div>
    );
  }

  if (!props.detail) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-[var(--chat-bg)] px-6">
        <Empty description="未找到对应的精品对话" />
      </div>
    );
  }

  const detail = props.detail;
  const backClassName =
    "inline-flex h-9 items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3.5 text-[13px] font-medium text-[var(--chat-text-soft)] shadow-[var(--shadow-sm)] transition hover:text-[var(--chat-text)]";

  const backControl =
    props.embedded && props.onBack ? (
      <button type="button" onClick={props.onBack} className={backClassName}>
        <ArrowLeft className="h-4 w-4" />
        <span>返回</span>
      </button>
    ) : (
      <Link
        to={ROUTES.FEATURED_CONVERSATIONS}
        target="_blank"
        rel="noreferrer"
        className={backClassName}
      >
        <ArrowLeft className="h-4 w-4" />
        <span>返回</span>
      </Link>
    );

  return (
    <div className="relative flex h-full w-full flex-col overflow-hidden bg-[var(--chat-bg)] text-[var(--chat-text)]">
      <div className="pointer-events-none absolute left-4 top-4 z-20 sm:left-5 sm:top-5">
        <div className="pointer-events-auto">{backControl}</div>
      </div>

      {!detail.contentAvailable || !hasRenderableContent ? (
        <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-8 pt-16 sm:px-6">
          <div className="mx-auto max-w-[720px] rounded-[28px] border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)] px-6 py-10 sm:px-8">
            <div className="text-[18px] font-semibold tracking-tight text-[var(--chat-text)]">
              正文暂不可用
            </div>
            {detail.summary ? (
              <p className="mt-3 text-[14px] leading-7 text-[var(--chat-text-soft)]">
                {detail.summary}
              </p>
            ) : null}
            <p className="mt-4 text-[13px] leading-6 text-[var(--chat-text-muted)]">
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
            }}
            product={undefined}
            conversation={conversation!}
            readOnly
            onConversationChange={() => {}}
          />
        </div>
      )}
    </div>
  );
}
