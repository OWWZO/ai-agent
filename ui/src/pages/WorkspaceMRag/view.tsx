import classNames from "classnames";
import {
  ArrowLeft,
  BookOpenText,
  DatabaseZap,
  ExternalLink,
  Globe,
  Link2,
  LoaderCircle,
  RefreshCcw,
  Search,
  SendHorizontal,
  Square,
  Trash2,
  UploadCloud,
  X,
} from "lucide-react";
import { useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";
import {
  StaggerContainer,
  StaggerItem,
} from "@/components/ai-elements/animated-message";
import { motion } from "motion/react";
import { EmptyState } from "@/components/ui/empty-state";
import { ROUTES } from "@/router/routes";
import type {
  KnowledgeBase,
  KnowledgeBaseFile,
  MRagFullContentStatus,
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
        "group relative w-full rounded-2xl border px-4 py-3 text-left transition-all duration-200",
        selected
          ? "border-[var(--primary)]/30 bg-[var(--primary)]/10 shadow-[var(--shadow-sm)]"
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
                ? "bg-[var(--primary)]/10 text-[var(--primary)]"
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
        {/* Source type icon */}
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

        {/* Content */}
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

        {/* Actions */}
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
  const [isLibraryOpen, setIsLibraryOpen] = useState(!selectedKnowledgeBase);
  const [isEvidenceOpen, setIsEvidenceOpen] = useState(Boolean(selectedKnowledgeBase));
  const activeFullContentFile =
    files.find((file) => file.id === activeFullContentFileId) || null;
  const hasQueryResult = queryAnswer || queryError || queryRawChunks.length > 0;

  return (
    <div className="flex h-full flex-col bg-[var(--page-gradient)] text-[var(--chat-text)]">
      {/* ── Header ── */}
      <div className="shrink-0 border-b border-[var(--chat-border)] bg-[var(--chat-surface)]/80 px-5 py-3 backdrop-blur-md">
        <div className="mx-auto flex max-w-[1480px] flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          {/* Left: icon + title */}
          <div className="flex items-center gap-3">
            {!embedded && (
              <Link
                to={ROUTES.HOME}
                className="flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--chat-border)] text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
              >
                <ArrowLeft className="h-4 w-4" />
              </Link>
            )}
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--primary)]/10 text-[var(--primary)]">
              <DatabaseZap className="h-4.5 w-4.5" />
            </div>
            <div>
              <h1 className="text-[15px] font-semibold tracking-tight text-[var(--chat-text)]">
                MRAG 智能问答工作台
              </h1>
              <p className="text-[12px] text-[var(--chat-text-muted)]">
                以问答为中心管理资料、证据和检索过程
              </p>
            </div>
          </div>

          {/* Right: Workspace Actions */}
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => setIsLibraryOpen((value) => !value)}
              className={classNames(
                "inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-[12px] font-medium transition",
                isLibraryOpen
                  ? "border-[var(--primary)]/25 bg-[var(--primary)]/10 text-[var(--primary)]"
                  : "border-[var(--chat-border)] text-[var(--chat-text-muted)] hover:bg-[var(--chat-surface-soft)]"
              )}
            >
              <DatabaseZap className="h-3.5 w-3.5" />
              知识源
            </button>
            <button
              type="button"
              onClick={() => setIsEvidenceOpen((value) => !value)}
              className={classNames(
                "inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-[12px] font-medium transition",
                isEvidenceOpen
                  ? "border-[var(--primary)]/25 bg-[var(--primary)]/10 text-[var(--primary)]"
                  : "border-[var(--chat-border)] text-[var(--chat-text-muted)] hover:bg-[var(--chat-surface-soft)]"
              )}
            >
              <BookOpenText className="h-3.5 w-3.5" />
              证据
            </button>
            {!embedded && <WorkspaceToolSwitcher />}
          </div>
        </div>
      </div>

      {/* ── Body ── */}
      <div className="min-h-0 flex-1 overflow-hidden px-4 py-4">
        <div className="mx-auto grid h-full max-w-[1540px] grid-cols-1 gap-4 lg:grid-cols-[minmax(0,320px)_minmax(520px,1fr)_minmax(0,360px)]">
          {/* 左侧知识源面板，负责选择上下文，不抢占问答焦点。 */}
          <motion.aside
            animate={{
              opacity: isLibraryOpen ? 1 : 0,
              x: isLibraryOpen ? 0 : -18,
            }}
            transition={{
              duration: 0.22,
              ease: [0.16, 1, 0.3, 1],
            }}
            className={classNames(
              "min-h-0 overflow-hidden rounded-[28px] border border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[var(--shadow-sm)]",
              isLibraryOpen ? "flex flex-col" : "hidden"
            )}
          >
            <div className="flex items-start justify-between gap-3 border-b border-[var(--chat-border)] px-4 py-4">
              <div>
                <div className="text-[12px] font-semibold uppercase tracking-wider text-[var(--chat-text-muted)]">
                  知识源
                </div>
                <div className="mt-1 text-[13px] text-[var(--chat-text-soft)]">
                  选择问答的上下文范围
                </div>
              </div>
              <ActionButton
                label="刷新"
                icon={<RefreshCcw className="h-3.5 w-3.5" />}
                onClick={onRefreshKnowledgeBases}
                loading={knowledgeBasesLoading}
                variant="ghost"
              />
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-3">
              <div className="space-y-2">
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
                          onSelect={() => onSelectKnowledgeBase(kb.id)}
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
            </div>

            <div className="shrink-0 border-t border-[var(--chat-border)] p-3">
              {!isCreateFormOpen ? (
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
                    className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--primary)]/30"
                  />
                  <textarea
                    value={createKnowledgeBaseDesc}
                    onChange={(e) => onCreateKnowledgeBaseDescChange(e.target.value)}
                    rows={2}
                    placeholder="用途描述，可选"
                    className="w-full resize-none rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--primary)]/30"
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
              )}
            </div>
          </motion.aside>

          {/* 中间问答舞台，所有主要注意力都落在这里。 */}
          <main
            className={classNames(
              "flex min-h-0 flex-col overflow-hidden rounded-[30px] border border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[var(--shadow-md)]",
              !isLibraryOpen && !isEvidenceOpen && "lg:col-span-3",
              isLibraryOpen && !isEvidenceOpen && "lg:col-span-2",
              !isLibraryOpen && isEvidenceOpen && "lg:col-span-2"
            )}
          >
            {(!selectedKnowledgeBase || hasQueryResult) ? (
              <div className="shrink-0 border-b border-[var(--chat-border)] px-5 py-3">
                <div className="flex flex-col gap-2 xl:flex-row xl:items-center xl:justify-between">
                  {!selectedKnowledgeBase ? (
                    <div className="min-w-0">
                      <h2 className="truncate text-[20px] font-semibold tracking-tight text-[var(--chat-text)]">
                        选择知识源后开始问答
                      </h2>
                      <p className="mt-1 max-w-[72ch] text-[13px] leading-6 text-[var(--chat-text-muted)]">
                        左侧选择或创建知识源后，系统会把导入资料作为回答依据。
                      </p>
                    </div>
                  ) : (
                    <div className="min-w-0" />
                  )}
                  <div className="flex flex-wrap items-center gap-2">
                    {hasQueryResult && (
                      <ActionButton
                        label="清空回答"
                        icon={<Trash2 className="h-3.5 w-3.5" />}
                        onClick={onClearQueryResult}
                        variant="ghost"
                      />
                    )}
                  </div>
                </div>
              </div>
            ) : null}

            <div className="min-h-0 flex-1 overflow-y-auto px-5 py-6">
              {!selectedKnowledgeBase ? (
                <div className="flex min-h-full items-center justify-center">
                  <EmptyState
                    icon={DatabaseZap}
                    title="先选一个知识源"
                    description="知识源决定 MRAG 的检索范围，选中后再导入文件或网页链接。"
                  />
                </div>
              ) : queryAnswer || queryError || querying ? (
                <motion.div
                  initial={{
                    opacity: 0,
                    y: 10,
                  }}
                  animate={{
                    opacity: 1,
                    y: 0,
                  }}
                  transition={{
                    duration: 0.35,
                    ease: [0.16, 1, 0.3, 1],
                  }}
                  className="mx-auto max-w-[900px]"
                >
                  {queryError ? (
                    <div className="rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
                      {queryError}
                    </div>
                  ) : queryAnswer ? (
                    <article className="rounded-[22px] border border-[var(--chat-border)] bg-[var(--chat-surface)] px-6 py-6 shadow-[var(--shadow-xs)]">
                      <div className="mb-4 flex items-center gap-2 text-[12px] font-medium text-[var(--chat-text-muted)]">
                        <Search className="h-3.5 w-3.5" />
                        回答
                      </div>
                      <MarkdownRenderer
                        markDownContent={queryAnswer}
                        isStreaming={querying}
                        className="text-[15px] leading-8"
                      />
                    </article>
                  ) : (
                    <div className="flex items-center justify-center py-16 text-[13px] text-[var(--chat-text-muted)]">
                      <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                      正在检索资料并组织回答...
                    </div>
                  )}
                </motion.div>
              ) : files.length ? (
                <div className="flex min-h-full items-center justify-center">
                  <div className="max-w-[560px] text-center">
                    <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-3xl bg-[var(--primary)]/10 text-[var(--primary)]">
                      <Search className="h-6 w-6" />
                    </div>
                    <h2 className="mt-5 text-[22px] font-semibold tracking-tight text-[var(--chat-text)]">
                      输入问题，开始检索
                    </h2>
                    <p className="mt-3 text-[14px] leading-7 text-[var(--chat-text-muted)]">
                      回答会在这里集中展示，右侧保留可追溯的原文和证据。
                    </p>
                  </div>
                </div>
              ) : (
                <div className="flex min-h-full items-center justify-center">
                  <EmptyState
                    icon={UploadCloud}
                    title="先导入资料"
                    description="右侧上传文件或添加网页链接，处理完成后就能开始提问。"
                  />
                </div>
              )}
            </div>

            <div className="shrink-0 border-t border-[var(--chat-border)] bg-[var(--chat-surface)]/92 px-4 py-3">
              <div className="rounded-[22px] border border-[var(--chat-border)] bg-[var(--chat-surface-soft)]/80 p-2.5">
                <textarea
                  value={question}
                  onChange={(e) => onQuestionChange(e.target.value)}
                  rows={3}
                  placeholder="问一个和资料有关的问题，例如：这份资料里对接流程的关键步骤是什么？"
                  className="w-full resize-none border-none bg-transparent px-2 py-2 text-[15px] leading-7 text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
                />
                <div className="flex flex-col gap-3 border-t border-[var(--chat-border)] px-1 pt-2.5 sm:flex-row sm:items-center sm:justify-between">
                  <span className="min-w-0 truncate text-[12px] text-[var(--chat-text-muted)]">
                    基于右侧证据回答
                  </span>
                  <div className="flex items-center gap-2">
                    {queryRawChunks.length > 0 && (
                      <button
                        type="button"
                        onClick={() => setShowDebug((value) => !value)}
                        className={classNames(
                          "inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-medium transition-colors",
                          showDebug
                            ? "bg-[var(--primary)]/10 text-[var(--primary)]"
                            : "text-[var(--chat-text-muted)] hover:bg-[var(--chat-surface)] hover:text-[var(--chat-text)]"
                        )}
                      >
                        <DatabaseZap className="h-3 w-3" />
                        调试数据
                      </button>
                    )}
                    {querying ? (
                      <ActionButton
                        label="停止"
                        icon={<Square className="h-3.5 w-3.5" />}
                        onClick={onStopQuery}
                        variant="secondary"
                      />
                    ) : (
                      <ActionButton
                        label="开始提问"
                        icon={<SendHorizontal className="h-3.5 w-3.5" />}
                        onClick={onSubmitQuery}
                        disabled={!selectedKnowledgeBase || !question.trim()}
                        variant="primary"
                      />
                    )}
                  </div>
                </div>
              </div>

              {showDebug && queryRawChunks.length > 0 && (
                <motion.div
                  initial={{
                    opacity: 0,
                    y: 8,
                  }}
                  animate={{
                    opacity: 1,
                    y: 0,
                  }}
                  transition={{
                    duration: 0.22,
                    ease: [0.16, 1, 0.3, 1],
                  }}
                  className="mt-3 overflow-hidden rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)]"
                >
                  <div className="flex items-center justify-between border-b border-[var(--chat-border)] px-4 py-2.5">
                    <span className="inline-flex items-center gap-2 text-[12px] font-medium text-[var(--chat-text-soft)]">
                      <DatabaseZap className="h-3.5 w-3.5" />
                      原始 SSE Chunk
                    </span>
                    <span className="rounded-md bg-[var(--chat-surface)] px-2 py-0.5 text-[11px] text-[var(--chat-text-muted)]">
                      {queryRawChunks.length} 条
                    </span>
                  </div>
                  <pre className="max-h-[180px] overflow-auto whitespace-pre-wrap px-4 py-3 font-mono text-[11px] leading-5 text-[var(--chat-text-muted)]">
                    {toPrettyJson(queryRawChunks)}
                  </pre>
                </motion.div>
              )}
            </div>
          </main>

          {/* 右侧证据面板，保留资料操作但从主问答区降噪。 */}
          <motion.aside
            animate={{
              opacity: isEvidenceOpen ? 1 : 0,
              x: isEvidenceOpen ? 0 : 18,
            }}
            transition={{
              duration: 0.22,
              ease: [0.16, 1, 0.3, 1],
            }}
            className={classNames(
              "min-h-0 overflow-hidden rounded-[28px] border border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[var(--shadow-sm)]",
              isEvidenceOpen ? "flex flex-col" : "hidden"
            )}
          >
            <div className="shrink-0 border-b border-[var(--chat-border)] px-4 py-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="text-[12px] font-semibold uppercase tracking-wider text-[var(--chat-text-muted)]">
                    证据与资料
                  </div>
                  <div className="mt-1 text-[13px] text-[var(--chat-text-soft)]">
                    {selectedKnowledgeBase ? `${files.length} 个资料源` : "先选择知识源"}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-1">
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
              </div>

              <div className="mt-4 space-y-2">
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

            <div className="min-h-0 flex-1 overflow-y-auto px-3 py-3">
              {filesError ? (
                <div className="mb-3 rounded-xl border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
                  {filesError}
                </div>
              ) : null}

              {!selectedKnowledgeBase ? (
                <div className="flex h-full items-center justify-center">
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
                <div className="flex h-full items-center justify-center">
                  <EmptyState
                    icon={UploadCloud}
                    title="还没有资料"
                    description="上传文件或添加网页链接，回答才有依据。"
                  />
                </div>
              )}
            </div>
          </motion.aside>
        </div>
      </div>
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
    </div>
  );
}

export default WorkspaceMRagView;
