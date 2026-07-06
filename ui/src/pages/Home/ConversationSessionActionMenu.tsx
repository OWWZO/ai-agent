import classNames from "classnames";
import { Pin, Star, Trash2 } from "lucide-react";

import type { ConversationSessionItem } from "@/services/agentConversation";

type ConversationSessionActionMenuProps = {
  session: ConversationSessionItem;
  canManageFeatured: boolean;
  onManageFeatured: (session: ConversationSessionItem) => void;
  onPin: (session: ConversationSessionItem) => void;
  onDelete: (session: ConversationSessionItem) => void;
};

const menuItemClassName =
  "flex w-full items-center gap-2 px-3 py-2 text-[12px] transition-colors";

export default function ConversationSessionActionMenu(
  props: ConversationSessionActionMenuProps
) {
  const { session, canManageFeatured, onManageFeatured, onPin, onDelete } =
    props;

  return (
    <div className="absolute right-2 top-full z-10 mt-1 w-36 rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] py-1 shadow-[var(--shadow-md)]">
      <button
        type="button"
        disabled={!canManageFeatured}
        title={
          canManageFeatured ? undefined : "请先让该会话至少产生一轮内容，再设为精品"
        }
        onClick={() => onManageFeatured(session)}
        className={classNames(
          menuItemClassName,
          canManageFeatured
            ? "text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
            : "cursor-not-allowed text-[var(--chat-text-muted)] opacity-60"
        )}
      >
        <Star className="h-3.5 w-3.5" />
        <span>设为精品</span>
      </button>
      <button
        type="button"
        onClick={() => onPin(session)}
        className={`${menuItemClassName} text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]`}
      >
        <Pin className="h-3.5 w-3.5" />
        <span>置顶</span>
      </button>
      <button
        type="button"
        onClick={() => onDelete(session)}
        className={`${menuItemClassName} text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--destructive)]`}
      >
        <Trash2 className="h-3.5 w-3.5" />
        <span>删除</span>
      </button>
    </div>
  );
}
