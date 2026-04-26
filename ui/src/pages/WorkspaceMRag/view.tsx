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
  resolveSourceSummary,
  toPrettyJson,
} from "./utils";

export type WorkspaceMRagViewProps = {
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

type SectionShellProps = {
  title: string;
  description: string;
  badge: string;
  children: ReactNode;
  actions?: ReactNode;
};

type ActionButtonProps = {
  label: string;
  icon: ReactNode;
  onClick?: () => void;
  href?: string;
  loading?: boolean;
  disabled?: boolean;
  variant?: "primary" | "secondary" | "danger";
};

function SectionShell(props: SectionShellProps) {
  const { title, description, badge, actions, children } = props;

  return (
    <section className="rounded-[30px] border border-white/70 bg-white/88 shadow-[0_24px_80px_-44px_rgba(15,23,42,0.35)] backdrop-blur-xl">
      <div className="border-b border-slate-200/80 px-5 py-4 sm:px-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full border border-sky-100 bg-sky-50 px-3 py-1 text-[12px] font-medium text-sky-700">
              <span>{badge}</span>
            </div>
            <h2 className="mt-3 text-[22px] font-semibold tracking-tight text-slate-900">
              {title}
            </h2>
            <p className="mt-1 text-sm leading-6 text-slate-500">{description}</p>
          </div>
          {actions ? <div className="shrink-0">{actions}</div> : null}
        </div>
      </div>
      <div className="px-5 py-5 sm:px-6">{children}</div>
    </section>
  );
}

function ActionButton(props: ActionButtonProps) {
  const { label, icon, onClick, href, loading, disabled, variant = "secondary" } = props;

  const className = classNames(
    "inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-semibold transition",
    variant === "primary" &&
      "bg-slate-900 text-white hover:bg-slate-800 disabled:bg-slate-300",
    variant === "secondary" &&
      "border border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900 disabled:text-slate-300",
    variant === "danger" &&
      "border border-rose-200 bg-rose-50 text-rose-600 hover:border-rose-300 hover:text-rose-700 disabled:text-rose-300"
  );

  const content = (
    <>
      {loading ? <LoaderCircle className="h-4 w-4 animate-spin" /> : icon}
      <span>{label}</span>
    </>
  );

  if (href) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noreferrer"
        className={classNames(className, disabled && "pointer-events-none opacity-50")}
      >
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
        "w-full rounded-[22px] border px-4 py-4 text-left transition",
        selected
          ? "border-sky-200 bg-sky-50/90 shadow-[0_18px_36px_-28px_rgba(14,116,144,0.6)]"
          : "border-slate-200 bg-slate-50/70 hover:border-slate-300 hover:bg-white"
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate text-[15px] font-semibold text-slate-900">
            {knowledgeBase.name}
          </div>
          <div className="mt-1 text-[12px] text-slate-500">
            {knowledgeBase.description || "暂无描述"}
          </div>
        </div>
        <span
          className={classNames(
            "rounded-full px-2.5 py-1 text-[11px] font-semibold",
            selected ? "bg-white text-sky-700" : "bg-white text-slate-500"
          )}
        >
          {knowledgeBase.chunkType}
        </span>
      </div>
      <div className="mt-3 flex flex-wrap gap-2 text-[12px] text-slate-400">
        <span>创建于 {formatWorkspaceDateTime(knowledgeBase.createdAt)}</span>
        <span>更新于 {formatWorkspaceDateTime(knowledgeBase.updatedAt)}</span>
      </div>
    </button>
  );
}

function FileRecordCard(props: {
  file: KnowledgeBaseFile;
  onDelete: (fileId: string) => void;
}) {
  const { file, onDelete } = props;
  const statusMeta = resolveFileStatusMeta(file.fileStatus);
  const isWebSource = file.sourceType === "url";

  return (
    <article className="rounded-[24px] border border-slate-200 bg-slate-50/75 p-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="truncate text-[16px] font-semibold text-slate-900">
              {file.title}
            </span>
            <span
              className={classNames(
                "rounded-full border px-2.5 py-1 text-[11px] font-semibold",
                statusMeta.className
              )}
            >
              {statusMeta.label}
            </span>
          </div>
          <div className="mt-2 break-all text-[13px] leading-6 text-slate-500">
            {file.sourceUrl}
          </div>
          <div className="mt-3 flex flex-wrap gap-2 text-[12px] text-slate-400">
            <span className="rounded-full bg-white px-2.5 py-1">
              {isWebSource ? "网页来源" : `文件 ${file.fileExt || "未知"}`}
            </span>
            <span className="rounded-full bg-white px-2.5 py-1">
              {formatFileDocCount(file)}
            </span>
            <span className="rounded-full bg-white px-2.5 py-1">
              来源 {resolveSourceSummary(file.sourceUrl)}
            </span>
            <span className="rounded-full bg-white px-2.5 py-1">
              更新时间 {formatWorkspaceDateTime(file.updatedAt)}
            </span>
          </div>
          {file.errorMessage ? (
            <div className="mt-3 rounded-[18px] border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] leading-5 text-rose-600">
              {file.errorMessage}
            </div>
          ) : null}
        </div>

        <div className="flex shrink-0 flex-wrap gap-2">
          {isWebSource ? (
            <ActionButton
              href={file.sourceUrl}
              label="打开原链接"
              icon={<ExternalLink className="h-4 w-4" />}
            />
          ) : (
            <>
              <ActionButton
                href={file.previewUrl}
                label="预览"
                icon={<ExternalLink className="h-4 w-4" />}
                disabled={!file.previewUrl}
              />
              <ActionButton
                href={file.downloadUrl}
                label="下载"
                icon={<ArrowLeft className="h-4 w-4 rotate-[135deg]" />}
                disabled={!file.downloadUrl}
              />
            </>
          )}
          <ActionButton
            label="删除"
            icon={<Trash2 className="h-4 w-4" />}
            variant="danger"
            onClick={() => onDelete(file.id)}
          />
        </div>
      </div>
    </article>
  );
}

export function WorkspaceMRagView(props: WorkspaceMRagViewProps) {
  const {
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

  const selectedKnowledgeBaseName =
    selectedKnowledgeBase?.name || "尚未选择知识库";

  return (
    <div className="relative min-h-full overflow-hidden bg-[linear-gradient(180deg,#f8fafc_0%,#eef6ff_100%)] text-slate-700">
      <div
        className="pointer-events-none absolute inset-0 opacity-35"
        style={{
          backgroundImage: "radial-gradient(#cbd5e1 1px, transparent 1px)",
          backgroundSize: "28px 28px",
        }}
      />
      <div
        className="pointer-events-none absolute left-[-12%] top-[-18%] h-[34rem] w-[34rem] rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(186,230,253,0.72) 0%, rgba(248,250,252,0) 72%)",
          filter: "blur(30px)",
        }}
      />
      <div className="pointer-events-none absolute bottom-[-10rem] right-[-10rem] h-[26rem] w-[26rem] rounded-full bg-[radial-gradient(circle,rgba(224,231,255,0.72)_0%,rgba(248,250,252,0)_72%)] blur-3xl" />

      <div className="relative z-10 mx-auto flex min-h-full max-w-[1480px] flex-col px-4 py-6 sm:px-6 lg:px-8">
        <header className="sticky top-4 z-20 mb-6 overflow-hidden rounded-[28px] border border-white/70 bg-white/82 shadow-[0_20px_60px_-32px_rgba(15,23,42,0.35)] backdrop-blur-xl">
          <div className="flex flex-col gap-4 px-5 py-4 sm:px-6">
            <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
              <div className="flex items-center gap-3">
                <Link
                  to={ROUTES.HOME}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-slate-200 bg-slate-50 text-slate-500 transition hover:border-slate-300 hover:bg-white hover:text-slate-900"
                >
                  <ArrowLeft className="h-4 w-4" />
                </Link>
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[linear-gradient(135deg,#0f766e_0%,#0ea5e9_55%,#2563eb_100%)] text-white shadow-[0_16px_32px_-20px_rgba(14,116,144,0.8)]">
                  <DatabaseZap className="h-5 w-5" />
                </div>
                <div>
                  <div className="text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-400">
                    Workspace
                  </div>
                  <h1 className="text-[20px] font-semibold tracking-tight text-slate-900 sm:text-[24px]">
                    MRAG 文件工作台
                  </h1>
                  <p className="mt-1 text-sm text-slate-500">
                    知识库列表、文件入库状态、原始资料访问与检索调试
                  </p>
                </div>
              </div>

              <WorkspaceToolSwitcher className="max-w-full xl:max-w-[720px]" />
            </div>

            <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]">
              <label className="flex min-w-0 items-center gap-3 rounded-[22px] border border-slate-200 bg-slate-50/85 px-4 py-3">
                <span className="shrink-0 text-[12px] font-semibold uppercase tracking-[0.18em] text-slate-400">
                  Tool URL
                </span>
                <input
                  value={toolBaseUrlDraft}
                  onChange={(event) => onToolBaseUrlChange(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      onApplyToolBaseUrl();
                    }
                  }}
                  placeholder="http://127.0.0.1:1601"
                  className="min-w-0 flex-1 border-none bg-transparent text-[14px] text-slate-700 outline-none placeholder:text-slate-400"
                />
              </label>
              <div className="flex flex-wrap gap-2">
                <ActionButton
                  label="应用地址"
                  icon={<Link2 className="h-4 w-4" />}
                  onClick={onApplyToolBaseUrl}
                  variant="primary"
                />
                <div className="inline-flex items-center rounded-full border border-slate-200 bg-white px-4 py-2 text-[12px] text-slate-500">
                  当前连接：{activeToolBaseUrl || "未配置"}
                </div>
              </div>
            </div>
          </div>
        </header>

        <div className="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
          <SectionShell
            badge="Knowledge Bases"
            title="知识库"
            description="选择当前工作知识库，或新建一个 MRAG 文件库。"
            actions={
              <ActionButton
                label="刷新"
                icon={<RefreshCcw className="h-4 w-4" />}
                onClick={onRefreshKnowledgeBases}
                loading={knowledgeBasesLoading}
              />
            }
          >
            <div className="space-y-4">
              {knowledgeBasesError ? (
                <div className="rounded-[18px] border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
                  {knowledgeBasesError}
                </div>
              ) : null}

              {knowledgeBases.length ? (
                <div className="space-y-3">
                  {knowledgeBases.map((knowledgeBase) => (
                    <KnowledgeBaseItem
                      key={knowledgeBase.id}
                      knowledgeBase={knowledgeBase}
                      selected={knowledgeBase.id === selectedKnowledgeBaseId}
                      onSelect={() => onSelectKnowledgeBase(knowledgeBase.id)}
                    />
                  ))}
                </div>
              ) : knowledgeBasesLoading ? (
                <div className="flex items-center justify-center rounded-[22px] border border-dashed border-slate-200 bg-slate-50/70 px-4 py-10 text-sm text-slate-400">
                  <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                  正在加载知识库...
                </div>
              ) : (
                <Empty
                  description="还没有知识库，先在下方创建一个。"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              )}

              <div className="rounded-[24px] border border-slate-200 bg-slate-50/70 p-4">
                <div className="text-[14px] font-semibold text-slate-900">创建知识库</div>
                <div className="mt-3 space-y-3">
                  <input
                    value={createKnowledgeBaseName}
                    onChange={(event) =>
                      onCreateKnowledgeBaseNameChange(event.target.value)
                    }
                    placeholder="例如：产品资料库"
                    className="w-full rounded-[18px] border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700 outline-none transition focus:border-sky-200"
                  />
                  <textarea
                    value={createKnowledgeBaseDesc}
                    onChange={(event) =>
                      onCreateKnowledgeBaseDescChange(event.target.value)
                    }
                    rows={3}
                    placeholder="补充这个知识库的用途，方便后续识别。"
                    className="w-full resize-none rounded-[18px] border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700 outline-none transition focus:border-sky-200"
                  />
                  <ActionButton
                    label="创建并切换"
                    icon={<DatabaseZap className="h-4 w-4" />}
                    onClick={onCreateKnowledgeBase}
                    loading={creatingKnowledgeBase}
                    disabled={!createKnowledgeBaseName.trim()}
                    variant="primary"
                  />
                </div>
              </div>
            </div>
          </SectionShell>

          <div className="grid gap-6 2xl:grid-cols-[minmax(0,1.1fr)_minmax(360px,0.9fr)]">
            <SectionShell
              badge="Files"
              title={selectedKnowledgeBase ? `${selectedKnowledgeBaseName} 的资料` : "文件工作区"}
              description="上传原始文件或网页链接，查看入库状态，并直接访问原始资料。"
              actions={
                <div className="flex flex-wrap gap-2">
                  <ActionButton
                    label="刷新文件"
                    icon={<RefreshCcw className="h-4 w-4" />}
                    onClick={onRefreshFiles}
                    loading={filesLoading}
                    disabled={!selectedKnowledgeBase}
                  />
                </div>
              }
            >
              {!selectedKnowledgeBase ? (
                <Empty
                  description="先从左侧选一个知识库，再开始上传或添加网页。"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              ) : (
                <div className="space-y-5">
                  <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                    <div className="rounded-[24px] border border-slate-200 bg-slate-50/70 p-4">
                      <div className="flex items-center gap-2 text-[15px] font-semibold text-slate-900">
                        <UploadCloud className="h-4 w-4 text-sky-600" />
                        <span>本地文件上传</span>
                      </div>
                      <p className="mt-2 text-sm leading-6 text-slate-500">
                        走两段式上传，先保存原文件，再发起入库任务，方便后续预览和下载。
                      </p>
                      <div className="mt-4">
                        <ActionButton
                          label="选择文件并入库"
                          icon={<UploadCloud className="h-4 w-4" />}
                          onClick={onUploadFiles}
                          loading={uploadingFiles}
                          variant="primary"
                        />
                      </div>
                    </div>

                    <div className="rounded-[24px] border border-slate-200 bg-slate-50/70 p-4">
                      <div className="flex items-center gap-2 text-[15px] font-semibold text-slate-900">
                        <Globe className="h-4 w-4 text-sky-600" />
                        <span>网页链接入库</span>
                      </div>
                      <p className="mt-2 text-sm leading-6 text-slate-500">
                        输入一个可访问的网页地址，系统会抓取正文并写入当前知识库。
                      </p>
                      <div className="mt-4 flex flex-col gap-3">
                        <input
                          value={webUrl}
                          onChange={(event) => onWebUrlChange(event.target.value)}
                          placeholder="https://example.com/article"
                          className="w-full rounded-[18px] border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700 outline-none transition focus:border-sky-200"
                        />
                        <ActionButton
                          label="提交链接"
                          icon={<Globe className="h-4 w-4" />}
                          onClick={onAddWebUrl}
                          loading={addingWebUrl}
                          disabled={!webUrl.trim()}
                        />
                      </div>
                    </div>
                  </div>

                  {filesError ? (
                    <div className="rounded-[18px] border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
                      {filesError}
                    </div>
                  ) : null}

                  <div className="flex flex-wrap items-center gap-2 text-[12px] text-slate-400">
                    <span className="rounded-full border border-slate-200 bg-white px-3 py-1">
                      当前知识库：{selectedKnowledgeBaseName}
                    </span>
                    <span className="rounded-full border border-slate-200 bg-white px-3 py-1">
                      文件数：{files.length}
                    </span>
                  </div>

                  {files.length ? (
                    <div className="space-y-3">
                      {files.map((file) => (
                        <FileRecordCard key={file.id} file={file} onDelete={onDeleteFile} />
                      ))}
                    </div>
                  ) : filesLoading ? (
                    <div className="flex items-center justify-center rounded-[22px] border border-dashed border-slate-200 bg-slate-50/70 px-4 py-10 text-sm text-slate-400">
                      <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                      正在刷新文件状态...
                    </div>
                  ) : (
                    <Empty
                      description="当前知识库还没有文件或网页记录。"
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                    />
                  )}
                </div>
              )}
            </SectionShell>

            <SectionShell
              badge="Retrieval Debugger"
              title="检索调试"
              description="针对当前知识库发起一次 MRAG 查询，查看流式回答和最终结果。首期只展示回答结果，不展示 chunk 明细浏览器。"
              actions={
                <div className="flex flex-wrap gap-2">
                  <ActionButton
                    label="清空结果"
                    icon={<Trash2 className="h-4 w-4" />}
                    onClick={onClearQueryResult}
                    disabled={!queryAnswer && !queryError && !queryRawChunks.length}
                  />
                </div>
              }
            >
              <div className="space-y-4">
                <div className="rounded-[24px] border border-slate-200 bg-slate-50/70 p-4">
                  <div className="flex flex-wrap items-center gap-2 text-[12px] text-slate-400">
                    <span className="rounded-full border border-slate-200 bg-white px-3 py-1">
                      当前知识库：{selectedKnowledgeBaseName}
                    </span>
                    <span className="rounded-full border border-slate-200 bg-white px-3 py-1">
                      返回类型：回答流
                    </span>
                  </div>
                  <textarea
                    value={question}
                    onChange={(event) => onQuestionChange(event.target.value)}
                    rows={6}
                    placeholder="输入一个问题，例如：这份资料里对接流程的关键步骤是什么？"
                    className="mt-4 w-full resize-none rounded-[20px] border border-slate-200 bg-white px-4 py-3 text-sm leading-6 text-slate-700 outline-none transition focus:border-sky-200"
                  />
                  <div className="mt-4 flex flex-wrap gap-2">
                    <ActionButton
                      label="开始检索"
                      icon={<Search className="h-4 w-4" />}
                      onClick={onSubmitQuery}
                      loading={querying}
                      disabled={!selectedKnowledgeBase || !question.trim()}
                      variant="primary"
                    />
                    <ActionButton
                      label="停止"
                      icon={<Square className="h-4 w-4" />}
                      onClick={onStopQuery}
                      disabled={!querying}
                    />
                  </div>
                </div>

                {queryError ? (
                  <div className="rounded-[18px] border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
                    {queryError}
                  </div>
                ) : null}

                <div className="rounded-[24px] border border-slate-200 bg-white p-4">
                  {queryAnswer ? (
                    <MarkdownRenderer
                      markDownContent={queryAnswer}
                      isStreaming={querying}
                      className="min-h-[280px] text-[14px] leading-7"
                    />
                  ) : querying ? (
                    <div className="flex min-h-[280px] items-center justify-center text-sm text-slate-400">
                      <LoaderCircle className="mr-2 h-4 w-4 animate-spin" />
                      正在接收回答流...
                    </div>
                  ) : (
                    <div className="flex min-h-[280px] items-center justify-center text-center text-sm text-slate-400">
                      发起一次检索后，这里会持续展示流式回答和最终结果。
                    </div>
                  )}
                </div>

                <details className="overflow-hidden rounded-[22px] border border-slate-200 bg-slate-50/80">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-slate-700">
                    <span className="inline-flex items-center gap-2">
                      <DatabaseZap className="h-4 w-4" />
                      <span>调试原始 SSE Chunk</span>
                    </span>
                    <span className="text-xs font-medium text-slate-400">
                      共 {queryRawChunks.length} 条
                    </span>
                  </summary>
                  <pre className="max-h-[280px] overflow-auto border-t border-slate-200 px-4 py-4 whitespace-pre-wrap font-mono text-[12px] leading-6 text-slate-600">
                    {toPrettyJson(queryRawChunks)}
                  </pre>
                </details>
              </div>
            </SectionShell>
          </div>
        </div>
      </div>
    </div>
  );
}

export default WorkspaceMRagView;
