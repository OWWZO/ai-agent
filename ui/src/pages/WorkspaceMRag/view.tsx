import classNames from "classnames";
import {
  ArrowLeft,
  BookOpenText,
  History,
  Copy,
  DatabaseZap,
  ExternalLink,
  Globe,
  Link2,
  LoaderCircle,
  MoreHorizontal,
  RefreshCcw,
  Search,
  SendHorizontal,
  Square,
  Star,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  UploadCloud,
  X,
} from "lucide-react";
import { useState } from "react";
import type { KeyboardEvent, ReactNode } from "react";
import { Link } from "react-router-dom";

import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";
import {
  StaggerContainer,
  StaggerItem,
} from "@/components/ai-elements/animated-message";
import { motion, AnimatePresence } from "motion/react";
import { EmptyState } from "@/components/ui/empty-state";
import { ROUTES } from "@/router/routes";
import type {
  KnowledgeBase,
  KnowledgeBaseFile,
  MRagFullContentStatus,
  MRagSessionSummary,
  MRagTurn,
} from "./types";
import {
  formatFileDocCount,
  formatWorkspaceDateTime,
  resolveFileStatusMeta,
  toPrettyJson,
} from "./utils";

export type WorkspaceMRagViewProps = {
  embedded?: boolean;
  knowledgeBases: KnowledgeBase[];
  knowledgeBasesLoading: boolean;
  knowledgeBasesError: string;
  selectedKnowledgeBaseId: string;
  onSelectKnowledgeBase: (kbId: string) => void;
  onRefreshKnowledgeBases: () => void;
  deletingKnowledgeBaseId: string;
  onDeleteKnowledgeBase: (kbId: string) => void;
  createKnowledgeBaseName: string;
  createKnowledgeBaseDesc: string;
  onCreateKnowledgeBaseNameChange: (value: string) => void;
  onCreateKnowledgeBaseDescChange: (value: string) => void;
  creatingKnowledgeBase: boolean;
  onCreateKnowledgeBase: () => void;
  selectedKnowledgeBase: KnowledgeBase | null;
  files: KnowledgeBaseFile[];
  filesLoading: boolean;
  filesError: string;
  uploadingFiles: boolean;
  addingWebUrl: boolean;
  webUrl: string;
  onWebUrlChange: (value: string) => void;
  onUploadFiles: () => void;
  onAddWebUrl: () => void;
  onRefreshFiles: () => void;
  activeFullContentFileId: string;
  fullContentLoading: boolean;
  fullContentDrawerOpen: boolean;
  fullContentTitle: string;
  fullContentStatus: MRagFullContentStatus;
  fullContentError: string;
  fullContentMarkdown: string;
  onOpenFullContent: (fileId: string) => void;
  onCloseFullContent: () => void;
  onDeleteFile: (fileId: string) => void;
  sessions: MRagSessionSummary[];
  sessionsLoading: boolean;
  sessionsError: string;
  activeSessionId: string;
  sessionTurns: MRagTurn[];
  onCreateSession: () => void;
  onSelectSession: (sessionId: string) => void;
  question: string;
  onQuestionChange: (value: string) => void;
  querying: boolean;
  queryAnswer: string;
  queryError: string;
  queryRawChunks: unknown[];
  onSubmitQuery: () => void;
  onStopQuery: () => void;
  onClearQueryResult: () => void;
};

/* ------------------------------------------------------------------ */
/*  Button                                                            */
/* ------------------------------------------------------------------ */

function ActionButton(props: {
  label: string;
  icon: ReactNode;
  onClick?: () => void;
  href?: string;
  loading?: boolean;
  disabled?: boolean;
  variant?: "primary" | "secondary" | "danger" | "ghost";
}) {
  const { label, icon, onClick, href, loading, disabled, variant = "secondary" } = props;

  const className = classNames(
    "inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-[13px] font-medium transition-all duration-200",
    variant === "primary" &&
      "bg-[var(--primary)] text-[var(--primary-foreground)] hover:opacity-90 disabled:opacity-40",
    variant === "secondary" &&
      "border border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-soft)] hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)] disabled:opacity-40",
    variant === "danger" &&
      "border border-rose-200 bg-rose-50 text-rose-600 hover:bg-rose-100 hover:text-rose-700",
    variant === "ghost" &&
      "text-[var(--chat-text-muted)] hover:text-[var(--chat-text)] hover:bg-[var(--chat-surface-soft)]"
  );

  const content = (
    <>
      {loading ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" /> : icon}
      <span>{label}</span>
    </>
  );

  if (href) {
    return (
      <a href={href} target="_blank" rel="noreferrer" className={classNames(className, disabled && "pointer-events-none opacity-40")}>
        {content}
      </a>
    );
  }

  return (
    <button type="button" onClick={onClick} disabled={disabled || loading} className={className}>
      {content}
    </button>
  );
}

/* ------------------------------------------------------------------ */
/*  Knowledge Base Card                                               */
/* ------------------------------------------------------------------ */

function KnowledgeBaseItem(props: {
  knowledgeBase: KnowledgeBase;
  selected: boolean;
  onSelect: () => void;
}) {
  const { knowledgeBase, selected, onSelect } = props;

  return (
    <button
      type="button"
      onClick={onSelect}
      className={classNames(
        "group relative w-full rounded-xl border px-3.5 py-3 text-left transition-all duration-200",
        selected
          ? "border-[var(--chat-accent)]/30 bg-[var(--chat-accent-soft)] shadow-[var(--shadow-xs)]"
          : "border-transparent hover:border-[var(--chat-border)] hover:bg-[var(--chat-surface-soft)]/60"
      )}
    >
      <div>
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="truncate text-[14px] font-semibold text-[var(--chat-text)]">
              {knowledgeBase.name}
            </div>
            <div className="mt-0.5 text-[12px] text-[var(--chat-text-muted)]">
              {knowledgeBase.description || "暂无描述"}
            </div>
            <div className="mt-1 font-mono text-[11px] text-[var(--chat-text-muted)]">
              ID: {knowledgeBase.id}
            </div>
          </div>
          <span
            className={classNames(
              "shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium",
              selected
                ? "bg-[var(--chat-accent)]/10 text-[var(--chat-accent)]"
                : "bg-[var(--chat-surface-soft)] text-[var(--chat-text-muted)]"
            )}
          >
            {knowledgeBase.chunkType}
          </span>
        </div>
        <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-[var(--chat-text-muted)]">
          <span>创建于 {formatWorkspaceDateTime(knowledgeBase.createdAt)}</span>
          <span>更新于 {formatWorkspaceDateTime(knowledgeBase.updatedAt)}</span>
        </div>
      </div>
    </button>
  );
}

/* ------------------------------------------------------------------ */
/*  File Row                                                          */
/* ------------------------------------------------------------------ */

function FileRecordRow(props: {
  file: KnowledgeBaseFile;
  fullContentActive: boolean;
  onOpenFullContent: (fileId: string) => void;
  onDelete: (fileId: string) => void;
}) {
  const { file, fullContentActive, onOpenFullContent, onDelete } = props;
  const statusMeta = resolveFileStatusMeta(file.fileStatus);
  const isWebSource = file.sourceType === "url";

  return (
    <div className="group border-b border-[var(--chat-border)] py-2.5 transition-colors hover:bg-[var(--chat-surface-soft)]/40 last:border-b-0">
      <div className="flex items-center gap-3">
        <div
          className={classNames(
            "flex h-7 w-7 shrink-0 items-center justify-center rounded-md",
            isWebSource
              ? "bg-[var(--status-info-bg)] text-[var(--status-info-text)]"
              : "bg-[var(--chat-surface-soft)] text-[var(--chat-text-muted)]"
          )}
        >
          {isWebSource ? (
            <Globe className="h-3.5 w-3.5" />
          ) : (
            <UploadCloud className="h-3.5 w-3.5" />
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <span className="truncate text-[13px] font-medium text-[var(--chat-text)]">
              {file.title}
            </span>
            <span
              className={classNames(
                "shrink-0 rounded px-1 py-0 text-[10px] font-medium leading-4",
                statusMeta.className
              )}
            >
              {statusMeta.label}
            </span>
            {file.errorMessage ? (
              <span
                className="shrink-0 text-rose-500"
                title={file.errorMessage}
              >
                <svg
                  className="h-3.5 w-3.5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
              </span>
            ) : null}
          </div>
          <div className="mt-0.5 flex items-center gap-1.5 truncate text-[11px] text-[var(--chat-text-muted)]">
            <span>{isWebSource ? "网页" : file.fileExt?.toUpperCase() || "文件"}</span>
            <span className="text-[var(--chat-border-strong)]">·</span>
            <span>{formatFileDocCount(file)}</span>
            <span className="text-[var(--chat-border-strong)]">·</span>
            <span>{formatWorkspaceDateTime(file.updatedAt)}</span>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
          {isWebSource ? (
            <a
              href={file.sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
              title="打开原链接"
            >
              <ExternalLink className="h-3.5 w-3.5" />
            </a>
          ) : (
            <>
              {file.previewUrl && (
                <a
                  href={file.previewUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                  title="预览"
                >
                  <ExternalLink className="h-3.5 w-3.5" />
                </a>
              )}
              {file.downloadUrl && (
                <a
                  href={file.downloadUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                  title="下载"
                >
                  <ArrowLeft className="h-3.5 w-3.5 rotate-[135deg]" />
                </a>
              )}
            </>
          )}
          <button
            type="button"
            onClick={() => onDelete(file.id)}
            className="flex h-7 w-7 items-center justify-center rounded-md text-[var(--chat-text-muted)] transition-colors hover:bg-rose-50 hover:text-rose-600"
            title="删除"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-2 pl-10 text-[11px] text-[var(--chat-text-muted)]">
        <span className="font-medium text-[var(--chat-text-soft)]">原始资料</span>
        <ActionButton
          label="查看正文"
          icon={<BookOpenText className="h-3.5 w-3.5" />}
          onClick={() => onOpenFullContent(file.id)}
          loading={fullContentActive}
          variant="ghost"
        />
      </div>
    </div>
  );
}

function FullContentPanel(props: {
  file: KnowledgeBaseFile | null;
  open: boolean;
  loading: boolean;
  title: string;
  contentStatus: MRagFullContentStatus;
  errorMessage: string;
  markdown: string;
  onClose: () => void;
}) {
  const { file, open, loading, title, contentStatus, errorMessage, markdown, onClose } = props;

  if (!open) {
    return null;
  }

  const showUnavailable = !loading && contentStatus !== "READY";
  const unavailableTitle =
    contentStatus === "PROCESSING" ? "正文生成中" : "正文暂不可用";

  return (
    <div className="fixed inset-y-0 right-0 z-50 w-full max-w-[560px] border-l border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[var(--shadow-xl)]">
      <div className="flex h-full flex-col">
        <div className="flex items-start justify-between gap-3 border-b border-[var(--chat-border)] px-5 py-4">
          <div className="min-w-0">
            <div className="text-[12px] font-semibold uppercase tracking-wider text-[var(--chat-text-muted)]">
              整篇正文
            </div>
            <div className="mt-1 truncate text-[15px] font-semibold text-[var(--chat-text)]">
              {title || "未命名资料"}
            </div>
          </div>
          <ActionButton
            label="关闭"
            icon={<ArrowLeft className="h-3.5 w-3.5" />}
            onClick={onClose}
            variant="ghost"
          />
        </div>

        {file ? (
          <div className="border-b border-[var(--chat-border)] px-5 py-3">
            <div className="text-[12px] font-semibold text-[var(--chat-text-soft)]">
              原始资料
            </div>
            <div className="mt-2 flex flex-wrap gap-2">
              {file.sourceType === "url" ? (
                <ActionButton
                  label="打开原链接"
                  icon={<ExternalLink className="h-3.5 w-3.5" />}
                  href={file.sourceUrl}
                  variant="secondary"
                />
              ) : (
                <>
                  {file.previewUrl ? (
                    <ActionButton
                      label="预览"
                      icon={<ExternalLink className="h-3.5 w-3.5" />}
                      href={file.previewUrl}
                      variant="secondary"
                    />
                  ) : null}
                  {file.downloadUrl ? (
                    <ActionButton
                      label="下载"
                      icon={<ArrowLeft className="h-3.5 w-3.5 rotate-[135deg]" />}
                      href={file.downloadUrl}
                      variant="secondary"
                    />
                  ) : null}
                </>
              )}
            </div>
          </div>
        ) : null}

        <div className="min-h-0 flex-1 overflow-auto px-5 py-4">
          {loading ? (
            <div className="flex items-center justify-center py-16 text-[13px] text-[var(--chat-text-muted)]">
              <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
              正在加载正文...
            </div>
          ) : null}

          {showUnavailable ? (
            <div className="rounded-2xl border border-amber-100 bg-amber-50 px-4 py-4 text-amber-700">
              <div className="text-[14px] font-semibold">{unavailableTitle}</div>
              <div className="mt-2 text-[13px] leading-6">
                {errorMessage || "当前文件暂时没有可回显的正文内容。"}
              </div>
            </div>
          ) : null}

          {!loading && contentStatus === "READY" ? (
            <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-4 py-4">
              <MarkdownRenderer
                markDownContent={markdown}
                className="text-[14px] leading-7"
              />
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Side Drawer                                                       */
/* ------------------------------------------------------------------ */

function SideDrawer(props: {
  open: boolean;
  side: "left" | "right";
  title: string;
  subtitle?: string;
  onClose: () => void;
  headerExtra?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const { open, side, title, subtitle, onClose, headerExtra, children, footer } = props;

  return (
    <>
      <AnimatePresence>
        {open ? (
          <motion.button
            type="button"
            aria-label="关闭面板"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 z-40 bg-[oklch(0.2_0.01_60/0.18)] backdrop-blur-[2px]"
            onClick={onClose}
          />
        ) : null}
      </AnimatePresence>
      <aside
        aria-hidden={!open}
        className={classNames(
          "fixed inset-y-0 z-50 flex w-full max-w-[360px] flex-col border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[var(--shadow-xl)] transition-transform duration-200 ease-out",
          side === "left" ? "left-0 border-r" : "right-0 border-l",
          open
            ? "translate-x-0 opacity-100"
            : side === "left"
              ? "pointer-events-none -translate-x-full opacity-0"
              : "pointer-events-none translate-x-full opacity-0"
        )}
      >
        <div className="flex items-start justify-between gap-3 border-b border-[var(--chat-border)] px-4 py-4">
          <div className="min-w-0">
            <div className="text-[13px] font-semibold text-[var(--chat-text)]">{title}</div>
            {subtitle ? (
              <div className="mt-0.5 text-[12px] text-[var(--chat-text-muted)]">{subtitle}</div>
            ) : null}
          </div>
          <div className="flex shrink-0 items-center gap-1">
            {headerExtra}
            <button
              type="button"
              onClick={onClose}
              className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
        {footer ? (
          <div className="shrink-0 border-t border-[var(--chat-border)] p-3">{footer}</div>
        ) : null}
      </aside>
    </>
  );
}

/* ------------------------------------------------------------------ */
/*  Main View                                                         */
/* ------------------------------------------------------------------ */

export function WorkspaceMRagView(props: WorkspaceMRagViewProps) {
  const {
    embedded,
    knowledgeBases,
    knowledgeBasesLoading,
    knowledgeBasesError,
    selectedKnowledgeBaseId,
    onSelectKnowledgeBase,
    onRefreshKnowledgeBases,
    deletingKnowledgeBaseId,
    onDeleteKnowledgeBase,
    createKnowledgeBaseName,
    createKnowledgeBaseDesc,
    onCreateKnowledgeBaseNameChange,
    onCreateKnowledgeBaseDescChange,
    creatingKnowledgeBase,
    onCreateKnowledgeBase,
    selectedKnowledgeBase,
    files,
    filesLoading,
    filesError,
    uploadingFiles,
    addingWebUrl,
    webUrl,
    onWebUrlChange,
    onUploadFiles,
    onAddWebUrl,
    onRefreshFiles,
    activeFullContentFileId,
    fullContentLoading,
    fullContentDrawerOpen,
    fullContentTitle,
    fullContentStatus,
    fullContentError,
    fullContentMarkdown,
    onOpenFullContent,
    onCloseFullContent,
    onDeleteFile,
    sessions,
    sessionsLoading,
    sessionsError,
    activeSessionId,
    sessionTurns,
    onCreateSession,
    onSelectSession,
    question,
    onQuestionChange,
    querying,
    queryAnswer,
    queryError,
    queryRawChunks,
    onSubmitQuery,
    onStopQuery,
    onClearQueryResult,
  } = props;

  const [isCreateFormOpen, setIsCreateFormOpen] = useState(false);
  const [showDebug, setShowDebug] = useState(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [isLibraryOpen, setIsLibraryOpen] = useState(false);
  const [isEvidenceOpen, setIsEvidenceOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const activeFullContentFile =
    files.find((file) => file.id === activeFullContentFileId) || null;
  const hasQueryResult = Boolean(queryAnswer || queryError || queryRawChunks.length > 0);
  const hasSessionTurns = sessionTurns.length > 0;
  const pageTitle = selectedKnowledgeBase?.name || "MRAG 智能问答工作台";
  const workspaceLabel = "MRAG 智能问答工作台";

  const handleCopyAnswer = async () => {
    if (!queryAnswer) return;
    try {
      await navigator.clipboard.writeText(queryAnswer);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      // ignore clipboard failures
    }
  };

  const handleSubmit = () => {
    if (!selectedKnowledgeBase || !question.trim() || querying) return;
    onSubmitQuery();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      handleSubmit();
    }
  };

  return (
    <div className="relative flex h-full flex-col bg-[var(--chat-bg)] text-[var(--chat-text)]">
      {/* ── Minimal top bar ── */}
      <header className="relative z-10 shrink-0 px-4 pt-3 sm:px-6">
        <div className="mx-auto flex max-w-[920px] items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2.5">
            {!embedded && (
              <Link
                to={ROUTES.HOME}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                title="返回首页"
              >
                <ArrowLeft className="h-4 w-4" />
              </Link>
            )}
            <button
              type="button"
              onClick={() => setIsLibraryOpen(true)}
              className="min-w-0 truncate rounded-lg px-1.5 py-1 text-left text-[14px] font-medium text-[var(--chat-text)] transition hover:bg-[var(--chat-surface-soft)]"
              title="切换知识源"
            >
              <span className="sr-only">{workspaceLabel}</span>
              {pageTitle}
            </button>
          </div>

          <div className="flex shrink-0 items-center gap-1.5">
            <button
              type="button"
              onClick={() => setIsHistoryOpen(true)}
              className={classNames(
                "inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[12px] font-medium transition",
                isHistoryOpen
                  ? "border-[var(--chat-accent)]/30 bg-[var(--chat-accent-soft)] text-[var(--chat-accent)]"
                  : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
              )}
            >
              <History className="h-3.5 w-3.5" />
              历史
              <span className="rounded-full bg-[var(--chat-surface-soft)] px-1.5 text-[11px] tabular-nums">
                {sessions.length}
              </span>
            </button>
            <button
              type="button"
              onClick={() => setIsLibraryOpen(true)}
              className={classNames(
                "inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[12px] font-medium transition",
                isLibraryOpen
                  ? "border-[var(--chat-accent)]/30 bg-[var(--chat-accent-soft)] text-[var(--chat-accent)]"
                  : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
              )}
            >
              <DatabaseZap className="h-3.5 w-3.5" />
              知识源
            </button>
            <button
              type="button"
              onClick={() => setIsEvidenceOpen(true)}
              className={classNames(
                "inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[12px] font-medium transition",
                isEvidenceOpen
                  ? "border-[var(--chat-accent)]/30 bg-[var(--chat-accent-soft)] text-[var(--chat-accent)]"
                  : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
              )}
            >
              <BookOpenText className="h-3.5 w-3.5" />
              证据
              {selectedKnowledgeBase && files.length > 0 ? (
                <span className="rounded-full bg-[var(--chat-surface-soft)] px-1.5 text-[11px] tabular-nums">
                  {files.length}
                </span>
              ) : null}
            </button>
            <div className="ml-0.5 flex items-center rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] p-0.5 shadow-[var(--shadow-xs)]">
              <button
                type="button"
                className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                title="收藏"
              >
                <Star className="h-3.5 w-3.5" />
              </button>
              <div className="relative">
                <details className="group">
                  <summary className="flex h-7 w-7 cursor-pointer list-none items-center justify-center rounded-full text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)] [&::-webkit-details-marker]:hidden">
                    <MoreHorizontal className="h-3.5 w-3.5" />
                  </summary>
                  <div className="absolute right-0 top-full z-20 mt-2 w-40 overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] py-1 shadow-[var(--shadow-md)]">
                    {hasQueryResult ? (
                      <button
                        type="button"
                        onClick={onClearQueryResult}
                        className="flex w-full items-center gap-2 px-3 py-2 text-left text-[12px] text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)]"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        清空回答
                      </button>
                    ) : null}
                    {!embedded ? (
                      <div className="border-t border-[var(--chat-border)] px-2 py-1.5">
                        <WorkspaceToolSwitcher />
                      </div>
                    ) : null}
                  </div>
                </details>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* ── Center document stage ── */}
      <div className="relative min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-[760px] px-5 pb-44 pt-8 sm:px-8 sm:pt-10">
          {!selectedKnowledgeBase ? (
            <div className="flex min-h-[48vh] items-center justify-center">
              <EmptyState
                icon={DatabaseZap}
                title="先选一个知识源"
                description="知识源决定 MRAG 的检索范围，选中后再导入文件或网页链接。"
              />
            </div>
          ) : queryAnswer || queryError || querying ? (
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
            >
              {queryError ? (
                <div className="rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
                  {queryError}
                </div>
              ) : queryAnswer ? (
                <article className="mrag-document">
                  <MarkdownRenderer
                    markDownContent={queryAnswer}
                    isStreaming={querying}
                    className="mrag-document-body text-[15px] leading-8"
                  />
                  {!querying ? (
                    <div className="mt-8 flex items-center gap-1 text-[var(--chat-text-muted)]">
                      <button
                        type="button"
                        onClick={handleCopyAnswer}
                        className="flex h-8 w-8 items-center justify-center rounded-lg transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                        title={copied ? "已复制" : "复制"}
                      >
                        <Copy className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        className="flex h-8 w-8 items-center justify-center rounded-lg transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                        title="有用"
                      >
                        <ThumbsUp className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        className="flex h-8 w-8 items-center justify-center rounded-lg transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                        title="无用"
                      >
                        <ThumbsDown className="h-4 w-4" />
                      </button>
                    </div>
                  ) : null}
                </article>
              ) : (
                <div className="flex items-center justify-center py-20 text-[13px] text-[var(--chat-text-muted)]">
                  <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                  正在检索资料并组织回答...
                </div>
              )}

              {showDebug && queryRawChunks.length > 0 ? (
                <motion.div
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.22, ease: [0.16, 1, 0.3, 1] }}
                  className="mt-8 overflow-hidden rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)]"
                >
                  <div className="flex items-center justify-between border-b border-[var(--chat-border)] px-4 py-2.5">
                    <span className="inline-flex items-center gap-2 text-[12px] font-medium text-[var(--chat-text-soft)]">
                      <DatabaseZap className="h-3.5 w-3.5" />
                      原始 SSE Chunk
                    </span>
                    <span className="rounded-md bg-[var(--chat-surface-soft)] px-2 py-0.5 text-[11px] text-[var(--chat-text-muted)]">
                      {queryRawChunks.length} 条
                    </span>
                  </div>
                  <pre className="max-h-[220px] overflow-auto whitespace-pre-wrap px-4 py-3 font-mono text-[11px] leading-5 text-[var(--chat-text-muted)]">
                    {toPrettyJson(queryRawChunks)}
                  </pre>
                </motion.div>
              ) : null}
            </motion.div>
          ) : hasSessionTurns ? (
            <div className="space-y-6">
              {sessionTurns.map((turn, index) => (
                <article
                  key={turn.turnId || `${turn.createdAt}-${index}`}
                  className="rounded-3xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-5 py-4 shadow-[var(--shadow-xs)]"
                >
                  <div className="mb-3 text-[11px] uppercase tracking-wider text-[var(--chat-text-muted)]">
                    第 {index + 1} 轮
                  </div>
                  <div className="rounded-2xl bg-[var(--chat-surface-soft)] px-4 py-3 text-[14px] leading-7 text-[var(--chat-text)]">
                    {turn.question}
                  </div>
                  {turn.errorMessage ? (
                    <div className="mt-3 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
                      {turn.errorMessage}
                    </div>
                  ) : (
                    <div className="mt-4">
                      <MarkdownRenderer
                        markDownContent={turn.answerMarkdown}
                        isStreaming={false}
                        className="mrag-document-body text-[15px] leading-8"
                      />
                    </div>
                  )}
                </article>
              ))}
            </div>
          ) : files.length ? (
            <div className="flex min-h-[48vh] flex-col items-center justify-center text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[var(--chat-surface-soft)] text-[var(--chat-text-muted)]">
                <Search className="h-5 w-5" />
              </div>
              <h2 className="mt-5 text-[22px] font-semibold tracking-tight text-[var(--chat-text)]">
                输入问题，开始检索
              </h2>
              <p className="mt-2 max-w-[42ch] text-[14px] leading-7 text-[var(--chat-text-muted)]">
                回答会以文档形式居中展示，知识源与证据从顶部打开。
              </p>
            </div>
          ) : (
            <div className="flex min-h-[48vh] items-center justify-center">
              <EmptyState
                icon={UploadCloud}
                title="先导入资料"
                description="打开右侧证据面板，上传文件或添加网页链接后再提问。"
              />
            </div>
          )}
        </div>
      </div>

      {/* ── Floating composer ── */}
      <div className="pointer-events-none absolute inset-x-0 bottom-0 z-20 px-4 pb-5 pt-10 sm:px-6">
        <div className="pointer-events-auto mx-auto w-full max-w-[720px]">
          <div className="rounded-[28px] border border-[var(--chat-border)] bg-[var(--chat-surface)] p-3 shadow-[var(--shadow-lg)]">
            <textarea
              value={question}
              onChange={(e) => onQuestionChange(e.target.value)}
              onKeyDown={handleKeyDown}
              rows={2}
              placeholder="输入你的问题..."
              className="w-full resize-none border-none bg-transparent px-2 py-1.5 text-[15px] leading-7 text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
            />
            <div className="mt-1 flex items-center justify-between gap-2 px-1">
              <div className="flex min-w-0 items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => setIsEvidenceOpen(true)}
                  className="inline-flex h-8 max-w-[180px] items-center gap-1.5 truncate rounded-full bg-[var(--chat-surface-soft)] px-2.5 text-[12px] text-[var(--chat-text-muted)] transition hover:text-[var(--chat-text)]"
                  title={selectedKnowledgeBase ? selectedKnowledgeBase.name : "选择知识源"}
                >
                  <DatabaseZap className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">
                    {selectedKnowledgeBase
                      ? `${files.length} 份资料`
                      : "未选知识源"}
                  </span>
                </button>
                {queryRawChunks.length > 0 ? (
                  <button
                    type="button"
                    onClick={() => setShowDebug((value) => !value)}
                    className={classNames(
                      "inline-flex h-8 items-center gap-1 rounded-full px-2.5 text-[11px] font-medium transition",
                      showDebug
                        ? "bg-[var(--chat-accent-soft)] text-[var(--chat-accent)]"
                        : "text-[var(--chat-text-muted)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                    )}
                  >
                    <DatabaseZap className="h-3 w-3" />
                    调试
                  </button>
                ) : null}
              </div>
              <div className="flex items-center gap-2">
                {querying ? (
                  <button
                    type="button"
                    onClick={onStopQuery}
                    className="inline-flex h-9 items-center gap-1.5 rounded-full border border-[var(--chat-border)] px-3.5 text-[13px] font-medium text-[var(--chat-text-soft)] transition hover:bg-[var(--chat-surface-soft)]"
                  >
                    <Square className="h-3.5 w-3.5" />
                    停止
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleSubmit}
                    disabled={!selectedKnowledgeBase || !question.trim()}
                    className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--primary)] text-[var(--primary-foreground)] transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-35"
                    title="开始提问"
                    aria-label="开始提问"
                  >
                    <SendHorizontal className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>
          </div>
          {!selectedKnowledgeBase ? (
            <p className="mt-2 text-center text-[11px] text-[var(--chat-text-muted)]">
              请先从顶部打开知识源，再开始提问
            </p>
          ) : null}
        </div>
      </div>

      {/* ── Knowledge source drawer ── */}
      <SideDrawer
        open={isHistoryOpen}
        side="left"
        title="对话历史"
        subtitle="继续上一轮 MRAG 问答"
        onClose={() => setIsHistoryOpen(false)}
        headerExtra={
          <ActionButton
            label="新对话"
            icon={<History className="h-3.5 w-3.5" />}
            onClick={onCreateSession}
            variant="ghost"
          />
        }
      >
        {sessionsError ? (
          <div className="rounded-2xl border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
            {sessionsError}
          </div>
        ) : null}
        {sessionsLoading ? (
          <div className="py-6 text-center text-[12px] text-[var(--chat-text-muted)]">
            加载中...
          </div>
        ) : sessions.length === 0 ? (
          <div className="py-6 text-center text-[12px] text-[var(--chat-text-muted)]">
            暂无历史对话
          </div>
        ) : (
          <div className="space-y-2">
            {sessions.map((session) => (
              <button
                key={session.sessionId}
                type="button"
                onClick={() => onSelectSession(session.sessionId)}
                className={classNames(
                  "w-full rounded-2xl border px-3 py-3 text-left transition",
                  session.sessionId === activeSessionId
                    ? "border-[var(--chat-accent)]/30 bg-[var(--chat-accent-soft)]"
                    : "border-[var(--chat-border)] bg-[var(--chat-surface)] hover:bg-[var(--chat-surface-soft)]"
                )}
              >
                <div className="truncate text-[13px] font-semibold text-[var(--chat-text)]">
                  {session.title}
                </div>
                <div className="mt-1 line-clamp-2 text-[12px] leading-5 text-[var(--chat-text-muted)]">
                  {session.latestQuestion || "暂无问题"}
                </div>
              </button>
            ))}
          </div>
        )}
      </SideDrawer>

      <SideDrawer
        open={isLibraryOpen}
        side="left"
        title="知识源"
        subtitle="选择问答的上下文范围"
        onClose={() => setIsLibraryOpen(false)}
        headerExtra={
          <ActionButton
            label="刷新"
            icon={<RefreshCcw className="h-3.5 w-3.5" />}
            onClick={onRefreshKnowledgeBases}
            loading={knowledgeBasesLoading}
            variant="ghost"
          />
        }
        footer={
          !isCreateFormOpen ? (
            <button
              type="button"
              onClick={() => setIsCreateFormOpen(true)}
              className="flex w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-[var(--chat-border)] py-3 text-[13px] font-medium text-[var(--chat-text-muted)] transition hover:border-[var(--chat-border-strong)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
            >
              <DatabaseZap className="h-4 w-4" />
              新建知识库
            </button>
          ) : (
            <div className="space-y-2 rounded-2xl bg-[var(--chat-surface-soft)] p-3">
              <div className="text-[12px] font-semibold text-[var(--chat-text-soft)]">
                新建知识库
              </div>
              <input
                value={createKnowledgeBaseName}
                onChange={(e) => onCreateKnowledgeBaseNameChange(e.target.value)}
                placeholder="名称，如：产品知识源"
                className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--chat-accent)]/30"
              />
              <textarea
                value={createKnowledgeBaseDesc}
                onChange={(e) => onCreateKnowledgeBaseDescChange(e.target.value)}
                rows={2}
                placeholder="用途描述，可选"
                className="w-full resize-none rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--chat-accent)]/30"
              />
              <div className="flex gap-2">
                <ActionButton
                  label="创建"
                  icon={<DatabaseZap className="h-3.5 w-3.5" />}
                  onClick={onCreateKnowledgeBase}
                  loading={creatingKnowledgeBase}
                  disabled={!createKnowledgeBaseName.trim()}
                  variant="primary"
                />
                <ActionButton
                  label="取消"
                  icon={<X className="h-3.5 w-3.5" />}
                  onClick={() => setIsCreateFormOpen(false)}
                  variant="ghost"
                />
              </div>
            </div>
          )
        }
      >
        <div className="space-y-2 p-3">
          {knowledgeBasesError ? (
            <div className="rounded-xl border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
              {knowledgeBasesError}
            </div>
          ) : null}

          {knowledgeBases.length ? (
            <StaggerContainer
              key={`kbs-${knowledgeBases.length}`}
              staggerDelay={0.03}
            >
              {knowledgeBases.map((kb) => (
                <StaggerItem key={kb.id}>
                  <KnowledgeBaseItem
                    knowledgeBase={kb}
                    selected={kb.id === selectedKnowledgeBaseId}
                    onSelect={() => {
                      onSelectKnowledgeBase(kb.id);
                      setIsLibraryOpen(false);
                    }}
                  />
                </StaggerItem>
              ))}
            </StaggerContainer>
          ) : knowledgeBasesLoading ? (
            <div className="flex items-center justify-center py-8 text-[13px] text-[var(--chat-text-muted)]">
              <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
              正在加载...
            </div>
          ) : (
            <EmptyState
              icon={DatabaseZap}
              title="还没有知识库"
              description="创建一个知识库来开始管理文件"
            />
          )}
        </div>
      </SideDrawer>

      {/* ── Evidence drawer ── */}
      <SideDrawer
        open={isEvidenceOpen}
        side="right"
        title="证据与资料"
        subtitle={selectedKnowledgeBase ? `${files.length} 个资料源` : "先选择知识源"}
        onClose={() => setIsEvidenceOpen(false)}
        headerExtra={
          <div className="flex items-center gap-1">
            <ActionButton
              label="刷新"
              icon={<RefreshCcw className="h-3.5 w-3.5" />}
              onClick={onRefreshFiles}
              loading={filesLoading}
              disabled={!selectedKnowledgeBase}
              variant="ghost"
            />
            {selectedKnowledgeBase ? (
              <ActionButton
                label="删除库"
                icon={<Trash2 className="h-3.5 w-3.5" />}
                onClick={() => onDeleteKnowledgeBase(selectedKnowledgeBase.id)}
                loading={deletingKnowledgeBaseId === selectedKnowledgeBase.id}
                variant="danger"
              />
            ) : null}
          </div>
        }
      >
        <div className="border-b border-[var(--chat-border)] px-4 py-3">
          <div className="space-y-2">
            <div className="flex flex-wrap gap-2">
              <ActionButton
                label="上传文件"
                icon={<UploadCloud className="h-3.5 w-3.5" />}
                onClick={onUploadFiles}
                loading={uploadingFiles}
                disabled={!selectedKnowledgeBase}
                variant="secondary"
              />
            </div>
            <div className="flex min-w-0 items-center gap-2 rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-2">
              <Globe className="h-3.5 w-3.5 shrink-0 text-[var(--chat-text-muted)]" />
              <input
                value={webUrl}
                onChange={(e) => onWebUrlChange(e.target.value)}
                placeholder="粘贴网页链接"
                className="min-w-0 flex-1 border-none bg-transparent text-[13px] text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
                disabled={!selectedKnowledgeBase}
              />
              <ActionButton
                label="添加"
                icon={<Link2 className="h-3.5 w-3.5" />}
                onClick={onAddWebUrl}
                loading={addingWebUrl}
                disabled={!selectedKnowledgeBase || !webUrl.trim()}
                variant="primary"
              />
            </div>
          </div>
        </div>

        <div className="px-3 py-3">
          {filesError ? (
            <div className="mb-3 rounded-xl border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
              {filesError}
            </div>
          ) : null}

          {!selectedKnowledgeBase ? (
            <div className="flex h-full items-center justify-center py-12">
              <EmptyState
                icon={ArrowLeft}
                title="等待知识源"
                description="选中知识源后，这里会显示可引用的文件和网页。"
              />
            </div>
          ) : files.length ? (
            <StaggerContainer
              key={`files-${selectedKnowledgeBaseId}`}
              staggerDelay={0.04}
            >
              {files.map((file) => (
                <StaggerItem key={file.id}>
                  <FileRecordRow
                    file={file}
                    fullContentActive={
                      fullContentLoading && activeFullContentFileId === file.id
                    }
                    onOpenFullContent={onOpenFullContent}
                    onDelete={onDeleteFile}
                  />
                </StaggerItem>
              ))}
            </StaggerContainer>
          ) : filesLoading ? (
            <div className="flex items-center justify-center py-12 text-[13px] text-[var(--chat-text-muted)]">
              <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
              正在刷新文件...
            </div>
          ) : (
            <div className="flex h-full items-center justify-center py-12">
              <EmptyState
                icon={UploadCloud}
                title="还没有资料"
                description="上传文件或添加网页链接，回答才有依据。"
              />
            </div>
          )}
        </div>
      </SideDrawer>

      <FullContentPanel
        file={activeFullContentFile}
        open={fullContentDrawerOpen}
        loading={fullContentLoading}
        title={fullContentTitle}
        contentStatus={fullContentStatus}
        errorMessage={fullContentError}
        markdown={fullContentMarkdown}
        onClose={onCloseFullContent}
      />

      <style>{`
        .mrag-document-body {
          color: var(--chat-text);
        }
        .mrag-document-body table {
          width: 100%;
          border-collapse: collapse;
          margin: 1rem 0 1.25rem;
          font-size: 14px;
          overflow: hidden;
          border-radius: 12px;
          border: 1px solid var(--chat-border);
        }
        .mrag-document-body th,
        .mrag-document-body td {
          border: 1px solid var(--chat-border);
          padding: 10px 14px;
          text-align: left;
        }
        .mrag-document-body th {
          background: var(--chat-surface-soft);
          font-weight: 600;
          color: var(--chat-text-soft);
        }
        .mrag-document-body tr:nth-child(even) td {
          background: color-mix(in oklab, var(--chat-surface-soft) 45%, transparent);
        }
        .mrag-document-body blockquote {
          margin: 1rem 0;
          padding: 12px 16px;
          border-radius: 12px;
          border: 1px solid color-mix(in oklch, var(--chat-accent) 22%, var(--chat-border));
          background: var(--chat-accent-soft);
          color: var(--chat-text-soft);
        }
        .mrag-document-body h1,
        .mrag-document-body h2,
        .mrag-document-body h3 {
          margin-top: 1.5rem;
          margin-bottom: 0.65rem;
          font-weight: 650;
          letter-spacing: -0.01em;
        }
        .mrag-document-body ul,
        .mrag-document-body ol {
          padding-left: 1.25rem;
          margin: 0.65rem 0 1rem;
        }
        .mrag-document-body li {
          margin: 0.35rem 0;
        }
        .mrag-document-body p {
          margin: 0.55rem 0;
        }
      `}</style>
    </div>
  );
}

export default WorkspaceMRagView;
