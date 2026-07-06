import dayjs from "dayjs";
import { Link } from "react-router-dom";

import type { FeaturedConversationCard as FeaturedConversationCardModel } from "@/services/featuredConversation";
import { buildFeaturedConversationDetailPath } from "@/router/routes";

type FeaturedConversationCardProps = {
  card: FeaturedConversationCardModel;
};

function formatDateTime(value?: string) {
  if (!value) {
    return "未记录";
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format("YYYY-MM-DD HH:mm") : value;
}

export default function FeaturedConversationCard(
  props: FeaturedConversationCardProps
) {
  const { card } = props;
  const tags = Array.isArray(card.tags) ? card.tags : [];

  return (
    <Link
      to={buildFeaturedConversationDetailPath(card.featuredId)}
      className="group overflow-hidden rounded-[24px] border border-[var(--chat-border)] bg-[var(--chat-surface)]/92 shadow-[var(--shadow-sm)] transition-all duration-200 hover:-translate-y-0.5 hover:border-[var(--chat-border-strong)] hover:shadow-[var(--shadow-md)]"
    >
      <div className="relative h-36 overflow-hidden bg-[linear-gradient(135deg,oklch(0.96_0.03_240),oklch(0.91_0.06_190))]">
        {card.coverUrl ? (
          <img
            src={card.coverUrl}
            alt={card.title}
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
          />
        ) : (
          <div className="flex h-full items-end bg-[radial-gradient(circle_at_top_left,rgba(255,255,255,0.82),transparent_56%)] p-5">
            <div className="rounded-full bg-white/72 px-3 py-1 text-[12px] font-medium text-[var(--chat-text)] backdrop-blur-sm">
              精品案例
            </div>
          </div>
        )}
      </div>

      <div className="flex flex-col gap-4 p-5">
        <div className="space-y-2">
          <div className="line-clamp-1 text-[17px] font-medium text-[var(--chat-text)]">
            {card.title}
          </div>
          <p className="line-clamp-3 text-[13px] leading-6 text-[var(--chat-text-soft)]">
            {card.summary}
          </p>
        </div>

        {tags.length ? (
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-[var(--chat-surface-soft)] px-2.5 py-1 text-[11px] text-[var(--chat-text-soft)]"
              >
                {tag}
              </span>
            ))}
          </div>
        ) : null}

        {/* 公开卡片直接展示时间元信息，方便访客判断案例是否仍有参考价值。 */}
        <div className="space-y-1 text-[12px] text-[var(--chat-text-muted)]">
          <div>发布时间：{formatDateTime(card.publishedAt)}</div>
          <div>内容最近更新：{formatDateTime(card.contentLastActiveAt)}</div>
        </div>
      </div>
    </Link>
  );
}
