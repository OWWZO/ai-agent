import { Empty, Pagination, Spin } from "antd";
import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

import FeaturedConversationCard from "@/components/FeaturedConversationCard";
import { ROUTES } from "@/router/routes";
import type { FeaturedConversationPage } from "@/services/featuredConversation";

type FeaturedConversationsViewProps = {
  page: FeaturedConversationPage;
  loading: boolean;
  pageNo: number;
  pageSize: number;
  onPageChange: (pageNo: number) => void;
};

export function FeaturedConversationsView(
  props: FeaturedConversationsViewProps
) {
  const hasCards = props.page.list.length > 0;

  return (
    <div className="h-full w-full overflow-y-auto px-6 py-8 md:px-10 lg:px-12">
      <div className="mx-auto w-full max-w-[1180px]">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-[30px] font-medium text-[var(--chat-text)]">
              精品对话
            </h1>
            <p className="mt-2 text-[14px] text-[var(--chat-text-soft)]">
              浏览管理员公开发布的案例，直接查看完整会话回放。
            </p>
          </div>

          <Link
            to={ROUTES.HOME}
            className="inline-flex items-center gap-2 text-[13px] font-medium text-[var(--chat-text-soft)] transition-colors hover:text-[var(--chat-text)]"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>返回首页</span>
          </Link>
        </div>

        {props.loading ? (
          <div className="flex min-h-[320px] items-center justify-center rounded-[28px] border border-[var(--chat-border)] bg-[var(--chat-surface)]/72">
            <Spin size="large" />
          </div>
        ) : hasCards ? (
          <>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {props.page.list.map((card) => (
                <FeaturedConversationCard
                  key={card.featuredId}
                  card={card}
                />
              ))}
            </div>

            <div className="mt-8 flex justify-end">
              <Pagination
                current={props.pageNo}
                pageSize={props.pageSize}
                total={props.page.total}
                showSizeChanger={false}
                onChange={props.onPageChange}
              />
            </div>
          </>
        ) : (
          <div className="rounded-[28px] border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/66 py-16">
            <Empty description="暂无已发布的精品对话" />
          </div>
        )}
      </div>
    </div>
  );
}
