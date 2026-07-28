import { memo, useState, useCallback } from "react";
import { motion, AnimatePresence } from "motion/react";
import classNames from "classnames";
import {
  Bot,
  SquarePen,
  Search,
  MoreHorizontal,
  ClipboardList,
  DatabaseZap,
  MessagesSquare,
  Star,
  WandSparkles,
  X,
} from "lucide-react";
import type { ConversationSessionItem } from "@/services/agentConversation";

import ConversationSessionActionMenu from "./ConversationSessionActionMenu";
import { canFeatureConversationSession } from "./featuredConversationAdminModel";

type SidebarView =
  | "chat"
  | "mrag"
  | "image-generation"
  | "sop"
  | "sub-agents"
  | "featured";

type NavItem = {
  key: SidebarView;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
};

const navItems: NavItem[] = [
  {
    key: "chat",
    label: "对话",
    icon: MessagesSquare,
  },
  {
    key: "mrag",
    label: "MRAG",
    icon: DatabaseZap,
  },
  {
    key: "image-generation",
    label: "生图",
    icon: WandSparkles,
  },
  {
    key: "sop",
    label: "SOP",
    icon: ClipboardList,
  },
  {
    key: "sub-agents",
    label: "子 Agent",
    icon: Bot,
  },
  {
    key: "featured",
    label: "精品对话",
    icon: Star,
  },
];

type ConversationSidebarProps = {
  activeView: SidebarView;
  recentSessions: ConversationSessionItem[];
  recentSessionsLoading: boolean;
  selectedSessionId?: string;
  visitorUsername?: string;
  onNewChat: () => void;
  onSelectSession: (session: ConversationSessionItem) => void;
  onChangeView: (view: SidebarView) => void;
  onManageFeaturedConversation: (session: ConversationSessionItem) => void;
};

const ConversationSidebar = memo(function ConversationSidebar(
  props: ConversationSidebarProps
) {
  const {
    activeView,
    recentSessions,
    recentSessionsLoading,
    selectedSessionId,
    onNewChat,
    onSelectSession,
    onChangeView,
    onManageFeaturedConversation,
  } = props;

  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [hoveredSessionId, setHoveredSessionId] = useState<string | null>(null);
  const [expandedSessionId, setExpandedSessionId] = useState<string | null>(
    null
  );

  const filteredSessions = searchQuery.trim()
    ? recentSessions.filter((s) =>
      (s.title || "未命名会话")
        .toLowerCase()
        .includes(searchQuery.toLowerCase()))
    : recentSessions;

  const handleSearchToggle = useCallback(() => {
    setSearchOpen((prev) => !prev);
    if (searchOpen) {
      setSearchQuery("");
    }
  }, [searchOpen]);

  const handleMoreClick = useCallback(
    (e: React.MouseEvent, sessionId: string) => {
      e.stopPropagation();
      setExpandedSessionId((prev) =>
        prev === sessionId ? null : sessionId
      );
    },
    []
  );

  const handleConsoleAction = useCallback(
    (action: string, session: ConversationSessionItem) => {
      setExpandedSessionId(null);
      console.log(`[ConversationSidebar] ${action}:`, session.sessionId);
    },
    []
  );

  const handleManageFeatured = useCallback(
    (session: ConversationSessionItem) => {
      setExpandedSessionId(null);
      onManageFeaturedConversation(session);
    },
    [onManageFeaturedConversation]
  );

  return (
    <div className="hidden h-full w-[var(--chat-sidebar-width)] shrink-0 flex-col border-r border-[var(--chat-border)] bg-[var(--chat-nav)] lg:flex">
      {/* 顶部操作区 — Manus 侧栏风格 */}
      <div className="flex h-14 shrink-0 items-center justify-between px-3.5">
        <div className="text-[18px] font-semibold tracking-[-0.02em] text-[var(--chat-text)]">
          Reactor
        </div>
        <div className="flex items-center gap-0.5">
          <button
            type="button"
            onClick={handleSearchToggle}
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-soft)] transition-colors hover:bg-black/5 hover:text-[var(--chat-text)]"
            aria-label="搜索"
          >
            <Search className="h-[18px] w-[18px]" />
          </button>
          <button
            type="button"
            onClick={onNewChat}
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-soft)] transition-colors hover:bg-black/5 hover:text-[var(--chat-text)]"
            aria-label="新建任务"
          >
            <SquarePen className="h-[18px] w-[18px]" />
          </button>
        </div>
      </div>

      <div className="shrink-0 px-2 pb-2">
        <button
          type="button"
          onClick={onNewChat}
          className="flex h-9 w-full items-center gap-2.5 rounded-[10px] px-2.5 text-[14px] font-medium text-[var(--chat-text)] transition-colors hover:bg-black/[0.04]"
        >
          <SquarePen className="h-[18px] w-[18px]" />
          <span>新建任务</span>
        </button>

        <AnimatePresence>
          {searchOpen && (
            <motion.div
              initial={{
                height: 0,
                opacity: 0
              }}
              animate={{
                height: "auto",
                opacity: 1
              }}
              exit={{
                height: 0,
                opacity: 0
              }}
              transition={{
                duration: 0.2,
                ease: [0.16, 1, 0.3, 1],
              }}
              className="overflow-hidden"
            >
              <div className="relative mt-2">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="搜索会话..."
                  autoFocus
                  className="w-full rounded-[10px] border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 pr-8 text-[13px] text-[var(--chat-text)] outline-none transition-colors placeholder:text-[var(--chat-text-muted)] focus:border-[#0000004d]"
                />
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => setSearchQuery("")}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* 导航区 */}
      <div className="shrink-0 px-2 py-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeView === item.key;
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => onChangeView(item.key)}
              className={classNames(
                "flex h-9 w-full items-center gap-2.5 rounded-[10px] px-2.5 text-[14px] font-medium transition-colors",
                isActive
                  ? "bg-black/[0.08] text-[var(--chat-text)]"
                  : "text-[var(--chat-text)] hover:bg-black/[0.04]"
              )}
            >
              <Icon className="h-[18px] w-[18px] shrink-0" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>

      {/* 最近会话 */}
      <div className="flex min-h-0 flex-1 flex-col px-2 pt-3">
        <div className="mb-1.5 flex items-center justify-between px-2.5">
          <span className="text-[12px] font-medium text-[var(--chat-text-muted)]">
            任务
          </span>
          {recentSessionsLoading && (
            <span className="text-[11px] text-[var(--chat-text-muted)]">
              加载中...
            </span>
          )}
        </div>

        <div className="flex-1 overflow-y-auto scrollbar-hover">
          {filteredSessions.length === 0 ? (
            <div className="px-2.5 py-4 text-center text-[12px] text-[var(--chat-text-muted)]">
              {searchQuery.trim() ? "未找到匹配的会话" : "暂无会话"}
            </div>
          ) : (
            <div className="flex flex-col gap-0.5">
              {filteredSessions.map((session) => {
                const isActive = session.sessionId === selectedSessionId;
                const isHovered = session.sessionId === hoveredSessionId;
                const isExpanded = session.sessionId === expandedSessionId;

                return (
                  <div
                    key={session.sessionId}
                    className="relative"
                    onMouseEnter={() => setHoveredSessionId(session.sessionId)}
                    onMouseLeave={() => setHoveredSessionId(null)}
                  >
                    <button
                      type="button"
                      onClick={() => onSelectSession(session)}
                      className={classNames(
                        "group flex h-9 w-full items-center gap-2.5 rounded-[10px] px-2.5 text-left transition-colors",
                        isActive
                          ? "bg-black/[0.08] text-[var(--chat-text)]"
                          : "text-[var(--chat-text)] hover:bg-black/[0.04]"
                      )}
                    >
                      <span
                        className={classNames(
                          "size-3.5 shrink-0 rounded-full border-[1.5px]",
                          isActive
                            ? "border-[var(--chat-accent)] shadow-[inset_0_0_0_3px_var(--chat-accent)]"
                            : "border-[var(--chat-text-muted)]"
                        )}
                        aria-hidden
                      />
                      <span className="min-w-0 flex-1 truncate text-[14px] font-medium">
                        {session.title || "未命名会话"}
                      </span>
                      <span
                        className={classNames(
                          "shrink-0 transition-opacity",
                          isHovered || isExpanded
                            ? "opacity-100"
                            : "opacity-0"
                        )}
                      >
                        <button
                          type="button"
                          onClick={(e) =>
                            handleMoreClick(e, session.sessionId)
                          }
                          className="rounded p-0.5 text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
                        >
                          <MoreHorizontal className="h-3.5 w-3.5" />
                        </button>
                      </span>
                    </button>

                    {/* 更多操作下拉 */}
                    {isExpanded && (
                      <ConversationSessionActionMenu
                        session={session}
                        canManageFeatured={canFeatureConversationSession(
                          session
                        )}
                        onManageFeatured={handleManageFeatured}
                        onPin={(targetSession) =>
                          handleConsoleAction("pin", targetSession)
                        }
                        onDelete={(targetSession) =>
                          handleConsoleAction("delete", targetSession)
                        }
                      />
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

    </div>
  );
});

ConversationSidebar.displayName = "ConversationSidebar";

export default ConversationSidebar;
