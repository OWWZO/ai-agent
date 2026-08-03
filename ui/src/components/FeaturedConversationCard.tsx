import dayjs from "dayjs";
import { ArrowUpRight } from "lucide-react";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";

import type { FeaturedConversationCard as FeaturedConversationCardModel } from "@/services/featuredConversation";
import { buildFeaturedConversationDetailPath } from "@/router/routes";

type FeaturedConversationCardProps = {
  card: FeaturedConversationCardModel;
  variant?: "grid" | "row";
  onSelect?: (featuredId: string) => void;
};

function formatDateTime(value?: string) {
  if (!value) {
    return "";
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format("YYYY-MM-DD") : value;
}

function resolveAccent(indexSeed: string) {
  const palette = [
    "from-[oklch(0.93_0.03_250)] to-[oklch(0.97_0.01_90)]",
    "from-[oklch(0.93_0.03_180)] to-[oklch(0.97_0.01_90)]",
    "from-[oklch(0.93_0.03_40)] to-[oklch(0.97_0.01_90)]",
    "from-[oklch(0.93_0.025_300)] to-[oklch(0.97_0.01_90)]",
  ];
  let hash = 0;
  for (let i = 0; i < indexSeed.length; i += 1) {
    hash = (hash + indexSeed.charCodeAt(i) * (i + 1)) % palette.length;
  }
  return palette[hash];
}

function CardShell(props: {
  href: string;
  onSelect?: () => void;
  className: string;
  children: ReactNode;
}) {
  if (props.onSelect) {
    return (
      <button
        type="button"
        onClick={props.onSelect}
        className={props.className}
      >
        {props.children}
      </button>
    );
  }

  return (
    <Link
      to={props.href}
      target="_blank"
      rel="noreferrer"
      className={props.className}
    >
      {props.children}
    </Link>
  );
}

export default function FeaturedConversationCard(
  props: FeaturedConversationCardProps
) {
  const { card, variant = "grid", onSelect } = props;
  const tags = Array.isArray(card.tags) ? card.tags : [];
  const publishedLabel = formatDateTime(card.publishedAt);
  const accent = resolveAccent(card.featuredId || card.title);
  const href = buildFeaturedConversationDetailPath(card.featuredId);
  const handleSelect = onSelect
    ? () => onSelect(card.featuredId)
    : undefined;

  if (variant === "row") {
    return (
      <CardShell
        href={href}
        onSelect={handleSelect}
        className="group flex w-full items-stretch gap-4 rounded-[22px] border border-[var(--chat-border)] bg-[var(--chat-surface)] p-3 text-left transition duration-200 hover:border-[var(--chat-border-strong)] hover:bg-[var(--chat-surface-soft)]/50 hover:shadow-[var(--shadow-sm)] sm:gap-5 sm:p-4"
      >
        <div
          className={`relative h-20 w-20 shrink-0 overflow-hidden rounded-[16px] bg-gradient-to-br sm:h-24 sm:w-28 ${accent}`}
        >
          {card.coverUrl ? (
            <img
              src={card.coverUrl}
              alt={card.title}
              className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.04]"
            />
          ) : (
            <div className="flex h-full items-end p-3">
              <span className="text-[11px] font-medium text-[var(--chat-text-soft)]">
                案例
              </span>
            </div>
          )}
        </div>

        <div className="min-w-0 flex-1 py-0.5">
          <div className="flex items-start justify-between gap-3">
            <h3 className="line-clamp-1 text-[16px] font-semibold tracking-tight text-[var(--chat-text)]">
              {card.title}
            </h3>
            <ArrowUpRight className="mt-0.5 h-4 w-4 shrink-0 text-[var(--chat-text-muted)] opacity-0 transition group-hover:opacity-100" />
          </div>
          <p className="mt-1.5 line-clamp-2 text-[13px] leading-6 text-[var(--chat-text-muted)]">
            {card.summary}
          </p>
          <div className="mt-2.5 flex flex-wrap items-center gap-2">
            {tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-[var(--chat-surface-soft)] px-2 py-0.5 text-[11px] text-[var(--chat-text-soft)]"
              >
                {tag}
              </span>
            ))}
            {publishedLabel ? (
              <span className="text-[11px] text-[var(--chat-text-muted)]">
                {publishedLabel}
              </span>
            ) : null}
          </div>
        </div>
      </CardShell>
    );
  }

  return (
    <CardShell
      href={href}
      onSelect={handleSelect}
      className="group flex h-full w-full flex-col overflow-hidden rounded-[24px] border border-[var(--chat-border)] bg-[var(--chat-surface)] text-left transition duration-200 hover:-translate-y-0.5 hover:border-[var(--chat-border-strong)] hover:shadow-[var(--shadow-md)]"
    >
      <div
        className={`relative aspect-[16/10] overflow-hidden bg-gradient-to-br ${accent}`}
      >
        {card.coverUrl ? (
          <img
            src={card.coverUrl}
            alt={card.title}
            className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]"
          />
        ) : (
          <div className="flex h-full items-end p-5">
            <span className="inline-flex w-fit rounded-full bg-[var(--chat-surface)]/80 px-2.5 py-1 text-[11px] font-medium text-[var(--chat-text-soft)] backdrop-blur-sm">
              精品案例
            </span>
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-3 p-5">
        <div className="space-y-2">
          <div className="flex items-start justify-between gap-2">
            <h3 className="line-clamp-2 text-[16px] font-semibold leading-snug tracking-tight text-[var(--chat-text)]">
              {card.title}
            </h3>
            <ArrowUpRight className="mt-0.5 h-4 w-4 shrink-0 text-[var(--chat-text-muted)] opacity-0 transition group-hover:opacity-100" />
          </div>
          <p className="line-clamp-3 text-[13px] leading-6 text-[var(--chat-text-muted)]">
            {card.summary}
          </p>
        </div>

        <div className="mt-auto flex flex-wrap items-center gap-2 pt-1">
          {tags.slice(0, 3).map((tag) => (
            <span
              key={tag}
              className="rounded-full bg-[var(--chat-surface-soft)] px-2.5 py-1 text-[11px] text-[var(--chat-text-soft)]"
            >
              {tag}
            </span>
          ))}
          {publishedLabel ? (
            <span className="ml-auto text-[11px] text-[var(--chat-text-muted)]">
              {publishedLabel}
            </span>
          ) : null}
        </div>
      </div>
    </CardShell>
  );
}
