import { Empty } from "antd";
import classNames from "classnames";
import {
  ArrowLeft,
  DatabaseZap,
  ExternalLink,
  Globe,
  Link2,
  LoaderCircle,
  RefreshCcw,
  Search,
  Square,
  Trash2,
  UploadCloud,
} from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";
import { ROUTES } from "@/router/routes";
import type { KnowledgeBase, KnowledgeBaseFile } from "./types";
import {
  formatFileDocCount,
  formatWorkspaceDateTime,
  resolveFileStatusMeta,
  toPrettyJson,
} from "./utils";

export type WorkspaceMRagViewProps = {
  embedded?: boolean;
  toolBaseUrlDraft: string;
  activeToolBaseUrl: string;
  onToolBaseUrlChange: (value: string) => void;
  onApplyToolBaseUrl: () => void;
  knowledgeBases: KnowledgeBase[];
  knowledgeBasesLoading: boolean;
  knowledgeBasesError: string;
  selectedKnowledgeBaseId: string;
  onSelectKnowledgeBase: (kbId: string) => void;
  onRefreshKnowledgeBases: () => void;
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
        "group w-full rounded-xl border px-4 py-3 text-left transition-all duration-200",
        selected
          ? "border-[var(--primary)]/20 bg-[var(--primary)]/5"
          : "border-[var(--chat-border)] bg-[var(--chat-surface)] hover:border-[var(--chat-border-strong)] hover:bg-[var(--chat-surface-soft)]"
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="truncate text-[14px] font-semibold text-[var(--chat-text)]">
            {knowledgeBase.name}
          </div>
          <div className="mt-0.5 text-[12px] text-[var(--chat-text-muted)]">
            {knowledgeBase.description || "暂无描述"}
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
    </button>
  );
}

/* ------------------------------------------------------------------ */
/*  File Row                                                          */
/* ------------------------------------------------------------------ */

function FileRecordRow(props: {
  file: KnowledgeBaseFile;
  onDelete: (fileId: string) => void;
}) {
  const { file, onDelete } = props;
  const statusMeta = resolveFileStatusMeta(file.fileStatus);
  const isWebSource = file.sourceType === "url";

  return (
    <div className="group flex items-start gap-4 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4 transition-all hover:border-[var(--chat-border-strong)] hover:shadow-[var(--shadow-sm)]">
      {/* Source type indicator */}
      <div className={classNames(
        "flex h-9 w-9 shrink-0 items-center justify-center rounded-lg",
        isWebSource ? "bg-sky-50 text-sky-600" : "bg-[var(--chat-surface-soft)] text-[var(--chat-text-muted)]"
      )}>
        {isWebSource ? <Globe className="h-4 w-4" /> : <UploadCloud className="h-4 w-4" />}
      </div>

      {/* Content */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate text-[14px] font-medium text-[var(--chat-text)]">
            {file.title}
          </span>
          <span className={classNames("shrink-0 rounded-md px-1.5 py-0.5 text-[11px] font-medium", statusMeta.className)}>
            {statusMeta.label}
          </span>
        </div>
        <div className="mt-1 truncate text-[12px] text-[var(--chat-text-muted)]">
          {file.sourceUrl}
        </div>
        <div className="mt-2 flex flex-wrap gap-2">
          <span className="rounded-md bg-[var(--chat-surface-soft)] px-2 py-0.5 text-[11px] text-[var(--chat-text-muted)]">
            {isWebSource ? "网页" : file.fileExt?.toUpperCase() || "文件"}
          </span>
          <span className="rounded-md bg-[var(--chat-surface-soft)] px-2 py-0.5 text-[11px] text-[var(--chat-text-muted)]">
            {formatFileDocCount(file)}
          </span>
          <span className="rounded-md bg-[var(--chat-surface-soft)] px-2 py-0.5 text-[11px] text-[var(--chat-text-muted)]">
            {formatWorkspaceDateTime(file.updatedAt)}
          </span>
        </div>
        {file.errorMessage ? (
          <div className="mt-2 rounded-lg border border-rose-100 bg-rose-50 px-3 py-1.5 text-[12px] text-rose-600">
            {file.errorMessage}
          </div>
        ) : null}
      </div>

      {/* Actions */}
      <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
        {isWebSource ? (
          <a
            href={file.sourceUrl}
            target="_blank"
            rel="noreferrer"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
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
                className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
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
                className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
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
          className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-muted)] transition-colors hover:bg-rose-50 hover:text-rose-600"
          title="删除"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </button>
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
    toolBaseUrlDraft,
    activeToolBaseUrl,
    onToolBaseUrlChange,
    onApplyToolBaseUrl,
    knowledgeBases,
    knowledgeBasesLoading,
    knowledgeBasesError,
    selectedKnowledgeBaseId,
    onSelectKnowledgeBase,
    onRefreshKnowledgeBases,
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

  const selectedKnowledgeBaseName = selectedKnowledgeBase?.name || "尚未选择";

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
                MRAG 文件工作台
              </h1>
              <p className="text-[12px] text-[var(--chat-text-muted)]">
                知识库管理、文件入库与检索调试
              </p>
            </div>
          </div>

          {/* Right: Tool URL */}
          <div className="flex items-center gap-2">
            <div className="flex min-w-0 items-center gap-2 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-2">
              <span className="shrink-0 text-[11px] font-semibold uppercase tracking-wider text-[var(--chat-text-muted)]">
                Tool URL
              </span>
              <input
                value={toolBaseUrlDraft}
                onChange={(e) => onToolBaseUrlChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    onApplyToolBaseUrl();
                  }
                }}
                placeholder="http://127.0.0.1:1601"
                className="min-w-0 flex-1 border-none bg-transparent text-[13px] text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
              />
            </div>
            <ActionButton
              label="连接"
              icon={<Link2 className="h-3.5 w-3.5" />}
              onClick={onApplyToolBaseUrl}
              variant="primary"
            />
            <span className="hidden rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-2.5 py-1.5 text-[11px] text-[var(--chat-text-muted)] lg:inline-flex">
              {activeToolBaseUrl || "未配置"}
            </span>
            {!embedded && <WorkspaceToolSwitcher />}
          </div>
        </div>
      </div>

      {/* ── Body ── */}
      <div className="min-h-0 flex-1 overflow-auto">
        <div className="mx-auto flex h-full max-w-[1480px] flex-col gap-0 lg:flex-row">
          {/* ── Left: Knowledge Bases ── */}
          <div className="flex h-full w-full shrink-0 flex-col border-b border-[var(--chat-border)] bg-[var(--chat-surface)] lg:w-[300px] lg:border-b-0 lg:border-r">
            {/* KB Header */}
            <div className="flex items-center justify-between border-b border-[var(--chat-border)] px-4 py-3">
              <div className="text-[12px] font-semibold uppercase tracking-wider text-[var(--chat-text-muted)]">
                知识库
              </div>
              <ActionButton
                label="刷新"
                icon={<RefreshCcw className="h-3.5 w-3.5" />}
                onClick={onRefreshKnowledgeBases}
                loading={knowledgeBasesLoading}
                variant="ghost"
              />
            </div>

            {/* KB List */}
            <div className="min-h-0 flex-1 overflow-y-auto p-3">
              <div className="space-y-2">
                {knowledgeBasesError ? (
                  <div className="rounded-xl border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
                    {knowledgeBasesError}
                  </div>
                ) : null}

                {knowledgeBases.length ? (
                  knowledgeBases.map((kb) => (
                    <KnowledgeBaseItem
                      key={kb.id}
                      knowledgeBase={kb}
                      selected={kb.id === selectedKnowledgeBaseId}
                      onSelect={() => onSelectKnowledgeBase(kb.id)}
                    />
                  ))
                ) : knowledgeBasesLoading ? (
                  <div className="flex items-center justify-center py-8 text-[13px] text-[var(--chat-text-muted)]">
                    <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                    正在加载...
                  </div>
                ) : (
                  <Empty description="还没有知识库" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </div>
            </div>

            {/* Create KB */}
            <div className="shrink-0 border-t border-[var(--chat-border)] p-3">
              <div className="text-[12px] font-semibold text-[var(--chat-text-soft)]">新建知识库</div>
              <div className="mt-2 space-y-2">
                <input
                  value={createKnowledgeBaseName}
                  onChange={(e) => onCreateKnowledgeBaseNameChange(e.target.value)}
                  placeholder="名称，如：产品资料库"
                  className="w-full rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--primary)]/30"
                />
                <textarea
                  value={createKnowledgeBaseDesc}
                  onChange={(e) => onCreateKnowledgeBaseDescChange(e.target.value)}
                  rows={2}
                  placeholder="用途描述（可选）"
                  className="w-full resize-none rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--primary)]/30"
                />
                <ActionButton
                  label="创建"
                  icon={<DatabaseZap className="h-3.5 w-3.5" />}
                  onClick={onCreateKnowledgeBase}
                  loading={creatingKnowledgeBase}
                  disabled={!createKnowledgeBaseName.trim()}
                  variant="primary"
                />
              </div>
            </div>
          </div>

          {/* ── Right: Content ── */}
          <div className="flex min-h-0 flex-1 flex-col">
            {/* Tabs */}
            <div className="flex shrink-0 items-center gap-1 border-b border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2">
              <span className="text-[12px] font-semibold uppercase tracking-wider text-[var(--chat-text-muted)]">
                {selectedKnowledgeBase ? selectedKnowledgeBaseName : "文件工作区"}
              </span>
              <span className="ml-2 rounded-md bg-[var(--chat-surface-soft)] px-2 py-0.5 text-[11px] text-[var(--chat-text-muted)]">
                {files.length} 个文件
              </span>
              <div className="ml-auto flex items-center gap-1">
                <ActionButton
                  label="刷新文件"
                  icon={<RefreshCcw className="h-3.5 w-3.5" />}
                  onClick={onRefreshFiles}
                  loading={filesLoading}
                  disabled={!selectedKnowledgeBase}
                  variant="ghost"
                />
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-4">
              {!selectedKnowledgeBase ? (
                <div className="flex h-full items-center justify-center">
                  <Empty
                    description="从左侧选择一个知识库开始管理文件"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  />
                </div>
              ) : (
                <div className="mx-auto max-w-[900px] space-y-4">
                  {/* Upload toolbar */}
                  <div className="flex flex-wrap gap-2">
                    <ActionButton
                      label="上传文件"
                      icon={<UploadCloud className="h-3.5 w-3.5" />}
                      onClick={onUploadFiles}
                      loading={uploadingFiles}
                      variant="secondary"
                    />
                    <div className="flex min-w-0 flex-1 items-center gap-2 rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-1.5">
                      <Globe className="h-3.5 w-3.5 shrink-0 text-[var(--chat-text-muted)]" />
                      <input
                        value={webUrl}
                        onChange={(e) => onWebUrlChange(e.target.value)}
                        placeholder="输入网页链接..."
                        className="min-w-0 flex-1 border-none bg-transparent text-[13px] text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
                      />
                      <ActionButton
                        label="添加"
                        icon={<Link2 className="h-3.5 w-3.5" />}
                        onClick={onAddWebUrl}
                        loading={addingWebUrl}
                        disabled={!webUrl.trim()}
                        variant="primary"
                      />
                    </div>
                  </div>

                  {filesError ? (
                    <div className="rounded-xl border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
                      {filesError}
                    </div>
                  ) : null}

                  {/* File list */}
                  {files.length ? (
                    <div className="space-y-2">
                      {files.map((file) => (
                        <FileRecordRow key={file.id} file={file} onDelete={onDeleteFile} />
                      ))}
                    </div>
                  ) : filesLoading ? (
                    <div className="flex items-center justify-center py-12 text-[13px] text-[var(--chat-text-muted)]">
                      <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                      正在刷新文件...
                    </div>
                  ) : (
                    <div className="flex items-center justify-center py-12">
                      <Empty description="当前知识库还没有文件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    </div>
                  )}

                  {/* ── Retrieval ── */}
                  <div className="mt-6 border-t border-[var(--chat-border)] pt-4">
                    <div className="mb-3 flex items-center justify-between">
                      <span className="text-[12px] font-semibold uppercase tracking-wider text-[var(--chat-text-muted)]">
                        检索调试
                      </span>
                      {(queryAnswer || queryError || queryRawChunks.length > 0) && (
                        <ActionButton
                          label="清空"
                          icon={<Trash2 className="h-3.5 w-3.5" />}
                          onClick={onClearQueryResult}
                          variant="ghost"
                        />
                      )}
                    </div>

                    {/* Query input */}
                    <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4 shadow-[var(--shadow-sm)]">
                      <textarea
                        value={question}
                        onChange={(e) => onQuestionChange(e.target.value)}
                        rows={3}
                        placeholder="输入问题，例如：这份资料里对接流程的关键步骤是什么？"
                        className="w-full resize-none rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-4 py-3 text-[14px] leading-6 text-[var(--chat-text)] outline-none transition placeholder:text-[var(--chat-text-muted)] focus:border-[var(--primary)]/30"
                      />
                      <div className="mt-3 flex items-center justify-between">
                        <span className="text-[12px] text-[var(--chat-text-muted)]">
                          针对「{selectedKnowledgeBaseName}」检索
                        </span>
                        <div className="flex gap-2">
                          {querying ? (
                            <ActionButton
                              label="停止"
                              icon={<Square className="h-3.5 w-3.5" />}
                              onClick={onStopQuery}
                              variant="secondary"
                            />
                          ) : (
                            <ActionButton
                              label="开始检索"
                              icon={<Search className="h-3.5 w-3.5" />}
                              onClick={onSubmitQuery}
                              disabled={!selectedKnowledgeBase || !question.trim()}
                              variant="primary"
                            />
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Query result */}
                    {(queryAnswer || queryError || querying) && (
                      <div className="mt-3 rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4 shadow-[var(--shadow-sm)]">
                        {queryError ? (
                          <div className="text-[13px] text-rose-600">{queryError}</div>
                        ) : queryAnswer ? (
                          <MarkdownRenderer
                            markDownContent={queryAnswer}
                            isStreaming={querying}
                            className="text-[14px] leading-7"
                          />
                        ) : (
                          <div className="flex items-center justify-center py-8 text-[13px] text-[var(--chat-text-muted)]">
                            <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                            正在检索...
                          </div>
                        )}
                      </div>
                    )}

                    {/* Raw chunks debug */}
                    {queryRawChunks.length > 0 && (
                      <details className="mt-3 overflow-hidden rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)]">
                        <summary className="flex cursor-pointer items-center justify-between px-4 py-3 text-[13px] font-medium text-[var(--chat-text-soft)]">
                          <span className="inline-flex items-center gap-2">
                            <DatabaseZap className="h-3.5 w-3.5" />
                            调试原始 SSE Chunk
                          </span>
                          <span className="text-[11px] text-[var(--chat-text-muted)]">
                            共 {queryRawChunks.length} 条
                          </span>
                        </summary>
                        <pre className="max-h-[240px] overflow-auto border-t border-[var(--chat-border)] px-4 py-3 whitespace-pre-wrap font-mono text-[11px] leading-5 text-[var(--chat-text-muted)]">
                          {toPrettyJson(queryRawChunks)}
                        </pre>
                      </details>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default WorkspaceMRagView;
