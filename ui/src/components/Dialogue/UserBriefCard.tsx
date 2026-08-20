import { FC, memo, useMemo } from "react";
import { MessageSquareTextIcon, PaperclipIcon } from "lucide-react";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";

type BriefAttachment = {
  path: string;
  size?: number;
  isImage?: boolean;
};

type UserBriefCardProps = {
  tool: CHAT.Task;
};

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null
    ? (value as Record<string, unknown>)
    : {};
}

function asAttachments(raw: unknown): BriefAttachment[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      path: String(item.path || ""),
      size: typeof item.size === "number" ? item.size : undefined,
      isImage: Boolean(item.isImage),
    }))
    .filter((item) => item.path);
}

function basename(path: string): string {
  const normalized = path.replace(/\\/g, "/");
  const parts = normalized.split("/");
  return parts[parts.length - 1] || path;
}

/**
 * SendUserMessage / Brief 可见消息卡片（对标 cc-haha BriefTool 主输出通道）。
 */
const UserBriefCard: FC<UserBriefCardProps> = memo(({ tool }) => {
  const resultMap = asRecord(tool.resultMap);
  const nested = asRecord(resultMap.resultMap);
  const toolAny = tool as unknown as Record<string, unknown>;

  const message = String(
    nested.message || resultMap.message || toolAny.message || ""
  ).trim();
  const status = String(
    nested.status || resultMap.status || toolAny.status || "normal"
  ).toLowerCase();
  const attachments = useMemo(
    () =>
      asAttachments(
        nested.attachments || resultMap.attachments || toolAny.attachments
      ),
    [nested.attachments, resultMap.attachments, toolAny.attachments]
  );

  if (!message && attachments.length === 0) {
    return null;
  }

  const isProactive = status === "proactive";

  return (
    <div className="mt-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface)]/80 px-3.5 py-3 shadow-[0_1px_0_rgba(0,0,0,0.02)]">
      <div className="mb-2 flex items-center gap-2">
        <div className="flex size-7 items-center justify-center rounded-lg border border-[var(--chat-border)]/40 bg-[#f5f5f7] text-[var(--chat-text-muted)]">
          <MessageSquareTextIcon className="size-3.5" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-[12px] font-medium text-[var(--chat-text-soft)]">
            {isProactive ? "主动通知" : "给用户的消息"}
          </div>
        </div>
        {isProactive ? (
          <span className="rounded-full border border-[var(--chat-border)]/40 bg-[#f5f5f7] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-muted)]">
            proactive
          </span>
        ) : null}
      </div>
      {message ? (
        <MarkdownRenderer
          markDownContent={message}
          className="chat-markdown kimi-md text-[14px] leading-relaxed"
        />
      ) : null}
      {attachments.length > 0 ? (
        <ul className="mt-2 space-y-1 border-t border-[var(--chat-border)]/25 pt-2">
          {attachments.map((item) => (
            <li
              key={item.path}
              className="flex min-w-0 items-center gap-1.5 text-[12px] text-[var(--chat-text-soft)]"
            >
              <PaperclipIcon className="size-3 shrink-0 opacity-70" />
              <span className="truncate" title={item.path}>
                {basename(item.path)}
                {item.isImage ? " · 图片" : ""}
                {typeof item.size === "number" ? ` · ${item.size} B` : ""}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
});

UserBriefCard.displayName = "UserBriefCard";

export default UserBriefCard;
