import { Empty, Pagination, Spin } from "antd";
import { ArrowLeft, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";

import FeaturedConversationCard from "@/components/FeaturedConversationCard";
import { ROUTES } from "@/router/routes";
import type { FeaturedConversationPage } from "@/services/featuredConversation";

type FeaturedConversationsViewProps = {
  embedded?: boolean;
  page: FeaturedConversationPage;
  loading: boolean;
  pageNo: number;
  pageSize: number;
  onPageChange: (pageNo: number) => void;
  onSelectCard?: (featuredId: string) => void;
};

export function FeaturedConversationsView(
  props: FeaturedConversationsViewProps
) {
  const hasCards = props.page.list.length > 0;
  const featuredCount = props.page.total || props.page.list.length;

  return (
    <div className="h-full w-full overflow-y-auto bg-[var(--chat-bg)] text-[var(--chat-text)]">
      <div className="mx-auto w-full max-w-[1080px] px-5 pb-16 pt-10 sm:px-8 sm:pt-14">
        <div className="mb-8 flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="mb-3 inline-flex items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-2.5 py-1 text-[12px] text-[var(--chat-text-muted)]">
              <Sparkles className="h-3.5 w-3.5" />
              公开案例库
            </div>
            <h1 className="text-[36px] font-semibold tracking-tight text-[var(--chat-text)] sm:text-[42px]">
              精品对话
            </h1>
            <p className="mt-2 max-w-[48ch] text-[14px] leading-7 text-[var(--chat-text-muted)]">
              浏览管理员公开发布的高质量会话回放，直接查看完整过程与结论。
            </p>
          </div>

          {!props.embedded ? (
            <Link
              to={ROUTES.HOME}
              target="_blank"
              rel="noreferrer"
              className="inline-flex h-9 shrink-0 items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3.5 text-[13px] font-medium text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)]"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>返回首页</span>
            </Link>
          ) : null}
        </div>

        {hasCards && !props.loading ? (
          <div className="mb-5 flex items-center justify-between gap-3 text-[12px] text-[var(--chat-text-muted)]">
            <span>共 {featuredCount} 个案例</span>
            <span>点击卡片查看完整回放</span>
          </div>
        ) : null}

        {props.loading ? (
          <div className="flex min-h-[360px] items-center justify-center rounded-[28px] border border-[var(--chat-border)] bg-[var(--chat-surface)]">
            <Spin size="large" />
          </div>
        ) : hasCards ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {props.page.list.map((card) => (
                <FeaturedConversationCard
                  key={card.featuredId}
                  card={card}
                  variant="grid"
                  onSelect={props.onSelectCard}
                />
              ))}
            </div>

            {props.page.total > props.pageSize ? (
              <div className="mt-10 flex justify-center">
                <Pagination
                  current={props.pageNo}
                  pageSize={props.pageSize}
                  total={props.page.total}
                  showSizeChanger={false}
                  onChange={props.onPageChange}
                />
              </div>
            ) : null}
          </>
        ) : (
          <div className="rounded-[28px] border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/70 py-20">
            <Empty description="暂无已发布的精品对话" />
          </div>
        )}
      </div>
    </div>
  );
}
