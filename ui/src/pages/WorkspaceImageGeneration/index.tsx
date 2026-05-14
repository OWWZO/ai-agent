import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import classNames from "classnames";
import {
  ArrowLeft,
  Brush,
  Clock,
  Download,
  Eraser,
  ImagePlus,
  RefreshCcw,
  SendHorizontal,
  Settings,
  Sparkles,
  Trash2,
  UploadCloud,
  WandSparkles,
  X,
  ZoomIn,
} from "lucide-react";

import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";

import type {RequestMode,} from "./types";
import {
  checkerboardStyle,
  formatHistoryTime,
  resolveDownloadUrl,
  resolvePreviewUrl,
  toPrettyJson,
} from "./utils";
import { useImageGenerationConfig } from "./useImageGenerationConfig";
import { useImageGenerationHistory } from "./useImageGenerationHistory";
import { useImageEditor } from "./useImageEditor";
import {useImageGenerationSession,} from "./useImageGenerationSession";

interface WorkspaceImageGenerationProps {
  embedded?: boolean;
}

/* ─────────── 内部子组件 ─────────── */

/** 空状态 */
function EmptyCanvas({ mode }: { mode: RequestMode }) {
  return (
    <div className="flex h-full min-h-[360px] flex-col items-center justify-center gap-4 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-[var(--chat-surface-muted)] text-[var(--chat-text-muted)]">
        <WandSparkles className="h-7 w-7" />
      </div>
      <div>
        <p className="text-[15px] font-medium text-[var(--chat-text-soft)]">
          {mode === "edits"
            ? "上传参考图并输入描述，开始图生图"
            : mode === "chat"
              ? "输入 Prompt 测试对话接口"
              : "输入 Prompt 开始生成图片"}
        </p>
        <p className="mt-1 text-[13px] text-[var(--chat-text-muted)]">
          结果将在此处展示
        </p>
      </div>
    </div>
  );
}

/** 生成中状态 */
function GeneratingPlaceholder() {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl bg-[var(--chat-surface)] py-10">
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--primary)]/10">
        <Sparkles className="h-5 w-5 animate-pulse text-[var(--primary)]" />
      </div>
      <p className="text-sm font-medium text-[var(--chat-text-soft)]">
        正在生成图像...
      </p>
    </div>
  );
}

/** 单张结果图卡片 */
function ResultImageCard({
  url,
  label,
  downloadUrl,
}: {
  url: string;
  label: string;
  downloadUrl?: string;
}) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      className="group relative overflow-hidden rounded-xl bg-[var(--chat-surface)]"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {/* 棋盘格背景 */}
      <div className="p-2" style={checkerboardStyle}>
        <img
          src={url}
          alt={label}
          className="mx-auto max-h-[320px] w-full rounded-lg object-contain transition-transform duration-300"
          style={{transform: hovered ? "scale(1.02)" : "scale(1)",}}
        />
      </div>

      {/* Hover 操作遮罩 */}
      <div
        className={classNames(
          "absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/40 transition-opacity duration-200",
          hovered ? "opacity-100" : "opacity-0"
        )}
      >
        <div className="flex items-center gap-2">
          <a
            href={url}
            target="_blank"
            rel="noreferrer"
            className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-slate-800 shadow-sm transition hover:bg-white hover:scale-105"
            title="查看原图"
          >
            <ZoomIn className="h-4 w-4" />
          </a>
          {downloadUrl ? (
            <a
              href={downloadUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-slate-800 shadow-sm transition hover:bg-white hover:scale-105"
              title="下载"
            >
              <Download className="h-4 w-4" />
            </a>
          ) : null}
        </div>
      </div>

      {/* 底部标签 */}
      <div className="flex items-center gap-2 px-3 py-2">
        <span className="truncate text-[12px] font-medium text-[var(--chat-text-soft)]">
          {label}
        </span>
      </div>
    </div>
  );
}

/** 画廊网格 */
function ImageGallery({
  messages,
  statusText,
}: {
  messages: ReturnType<typeof useImageGenerationSession>["messages"];
  statusText: string;
}) {
  const hasLoading = messages.some(
    (m) => m.role === "assistant" && m.status === "loading"
  );

  const resultImages = messages.flatMap((msg) =>
    msg.role === "assistant" && msg.status !== "loading"
      ? msg.images.map((img) => ({
        ...img,
        messageId: msg.id
      }))
      : []
  );

  const errorMessages = messages.filter(
    (m): m is Extract<typeof m, { role: "assistant" }> =>
      m.role === "assistant" && m.status === "error"
  );

  if (!messages.length && !statusText) {
    return null;
  }

  return (
    <div className="space-y-4">
      {/* 加载中 */}
      {hasLoading && <GeneratingPlaceholder />}

      {/* 错误提示 */}
      {errorMessages.map((msg) => (
        <div
          key={msg.id}
          className="rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600"
        >
          {msg.error || msg.summary}
        </div>
      ))}

      {/* 图片网格 */}
      {resultImages.length > 0 && (
        <div
          className={classNames(
            "grid gap-3",
            resultImages.length === 1
              ? "grid-cols-1"
              : "grid-cols-1 sm:grid-cols-2"
          )}
        >
          {resultImages.map((img, index) => (
            <ResultImageCard
              key={`${img.messageId}-${img.url}-${index}`}
              url={img.url}
              label={img.label}
              downloadUrl={img.downloadUrl}
            />
          ))}
        </div>
      )}

      {/* 状态文字（非错误且没有图片时） */}
      {statusText && !hasLoading && resultImages.length === 0 && (
        <div className="rounded-xl bg-[var(--chat-surface-muted)] px-4 py-6 text-center text-sm text-[var(--chat-text-muted)]">
          {statusText}
        </div>
      )}
    </div>
  );
}

/** 图片预览弹窗 */
function ImagePreviewModal({
  src,
  onClose,
}: {
  src: string | null;
  onClose: () => void;
}) {
  if (!src) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
      onClick={onClose}
    >
      <img
        src={src}
        alt="预览"
        className="max-h-[90vh] max-w-[90vw] rounded-lg object-contain shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      />
    </div>
  );
}

/* ─────────── 主组件 ─────────── */

const WorkspaceImageGeneration: ReactorType.FC<WorkspaceImageGenerationProps> = ({embedded,}) => {
  const { config, updateConfig } = useImageGenerationConfig();
  const {
    historyBatches,
    historyTotal,
    historyPageNo,
    historyLoading,
    historyLoadingMore,
    historyError,
    loadHistory,
  } = useImageGenerationHistory();
  const {
    images,
    editingImage,
    brushSize,
    toolMode,
    editorImageRef,
    maskCanvasRef,
    addFiles,
    collectEffectiveImages,
    closeEditor,
    openEditor,
    removeImage,
    clearCurrentMask,
    refreshEditorLayout,
    buildMaskCompositeDataUrls,
    setBrushSize,
    setToolMode,
  } = useImageEditor({ mode: config.mode });
  const {
    prompt,
    setPrompt,
    messages,
    clearMessages,
    handleSend,
    statusText,
    statusTone,
    debugPayload,
  } = useImageGenerationSession({
    config,
    collectEffectiveImages,
    buildMaskCompositeDataUrls,
    reloadHistory: () => loadHistory(1, true),
  });

  const chatRef = useRef<HTMLDivElement>(null);
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [showDebug, setShowDebug] = useState(false);

  useEffect(() => {
    if (!chatRef.current) return;
    chatRef.current.scrollTo({
      top: chatRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [messages]);

  return (
    <div className="flex h-full flex-col bg-[var(--page-gradient)] text-[var(--chat-text)]">
      {/* ═══════ Header ═══════ */}
      <div className="shrink-0 border-b border-[var(--chat-border)] bg-[var(--chat-surface)]/80 px-5 py-3 backdrop-blur-md">
        <div className="mx-auto flex max-w-[1400px] items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {!embedded && (
              <Link
                to="/"
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-[var(--chat-border)] text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
              >
                <ArrowLeft className="h-4 w-4" />
              </Link>
            )}
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[var(--primary)]/10 text-[var(--primary)]">
              <WandSparkles className="h-4.5 w-4.5" />
            </div>
            <div>
              <h1 className="text-[15px] font-semibold tracking-tight text-[var(--chat-text)]">
                绘图智能体
              </h1>
              <p className="text-[12px] text-[var(--chat-text-muted)]">
                AI 图像生成工作台
              </p>
            </div>
          </div>

          {!embedded && <WorkspaceToolSwitcher />}
        </div>
      </div>

      {/* ═══════ 三栏主体 ═══════ */}
      <section className="workspace-fade-enter mx-auto flex w-full max-w-[1400px] flex-1 gap-4 overflow-hidden p-4">
        {/* ── 左栏：参数配置 ── */}
        <aside className="flex w-[240px] shrink-0 flex-col gap-4 overflow-y-auto">
          <div className="rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-4">
            <div className="mb-3 flex items-center gap-2 text-[var(--chat-text-muted)]">
              <Settings className="h-3.5 w-3.5" />
              <span className="text-[11px] font-semibold tracking-wide">
                生成参数
              </span>
            </div>

            <div className="space-y-3">
              <label className="block">
                <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                  模式
                </span>
                <select
                  value={config.mode}
                  onChange={(event) =>
                    updateConfig("mode", event.target.value as RequestMode)
                  }
                  className="w-full rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-sm text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                >
                  <option value="images">文生图</option>
                  <option value="edits">图生图</option>
                  <option value="chat">对话调试</option>
                </select>
              </label>

              {config.mode === "chat" ? (
                <>
                  <label className="block">
                    <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                      Base URL
                    </span>
                    <input
                      value={config.baseUrl}
                      onChange={(event) =>
                        updateConfig("baseUrl", event.target.value)
                      }
                      placeholder="https://..."
                      className="w-full rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-sm text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                      API Key
                    </span>
                    <input
                      type="password"
                      value={config.apiKey}
                      onChange={(event) =>
                        updateConfig("apiKey", event.target.value)
                      }
                      placeholder="sk-..."
                      className="w-full rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-sm font-mono tracking-wide text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                      Model
                    </span>
                    <input
                      value={config.model}
                      onChange={(event) =>
                        updateConfig("model", event.target.value)
                      }
                      className="w-full rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-sm font-mono text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                    />
                  </label>
                </>
              ) : null}

              <div className="grid grid-cols-2 gap-2">
                <label className="block">
                  <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                    尺寸
                  </span>
                  <input
                    value={config.size}
                    onChange={(event) =>
                      updateConfig("size", event.target.value)
                    }
                    className="w-full rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-sm font-mono text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                  />
                </label>
                <label className="block">
                  <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                    数量
                  </span>
                  <input
                    type="number"
                    min={1}
                    max={10}
                    value={config.n}
                    onChange={(event) =>
                      updateConfig(
                        "n",
                        Math.max(
                          1,
                          Math.min(10, Number(event.target.value) || 1)
                        )
                      )
                    }
                    className="w-full rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-sm font-mono text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                  />
                </label>
              </div>

              {config.mode === "edits" && (
                <label className="flex items-start gap-2.5 rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2.5 text-sm text-[var(--chat-text)]">
                  <input
                    type="checkbox"
                    checked={config.batchMode}
                    onChange={(event) =>
                      updateConfig("batchMode", event.target.checked)
                    }
                    className="mt-0.5 h-4 w-4 rounded border-[var(--chat-border)] text-[var(--primary)] focus:ring-[var(--primary)]/20"
                  />
                  <div>
                    <div className="text-[13px] font-medium text-[var(--chat-text)]">
                      多图批处理
                    </div>
                    <div className="mt-0.5 text-[11px] leading-4 text-[var(--chat-text-muted)]">
                      每张参考图独立请求，更稳定
                    </div>
                  </div>
                </label>
              )}
            </div>

            {statusText && (
              <div
                className={classNames(
                  "mt-3 rounded-lg px-3 py-2 text-[12px] font-medium",
                  statusTone === "success" &&
                    "bg-[var(--status-success-bg)] text-[var(--status-success-text)]",
                  statusTone === "error" &&
                    "bg-[var(--status-failed-bg)] text-[var(--status-failed-text)]",
                  statusTone === "default" &&
                    "bg-[var(--chat-surface-muted)] text-[var(--chat-text-muted)]"
                )}
              >
                {statusText}
              </div>
            )}
          </div>
        </aside>

        {/* ── 中栏：主画布 ── */}
        <main className="flex min-w-0 flex-1 flex-col gap-4 overflow-hidden">
          {/* 结果区域 */}
          <div
            ref={chatRef}
            className="flex-1 overflow-y-auto rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-4"
          >
            {messages.length === 0 ? (
              <EmptyCanvas mode={config.mode} />
            ) : (
              <ImageGallery messages={messages} statusText={statusText} />
            )}
          </div>

          {/* 输入区 */}
          <div className="shrink-0 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-3">
            <textarea
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void handleSend();
                }
              }}
              placeholder={
                config.mode === "edits"
                  ? "描述如何修改这些图片，例如：把天空替换成晚霞..."
                  : config.mode === "chat"
                    ? "输入对话内容..."
                    : "描述你要生成的画面内容..."
              }
              className="min-h-[80px] w-full resize-none rounded-lg border-none bg-transparent px-2 py-2 text-[15px] leading-6 text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
            />
            <div className="mt-2 flex flex-wrap items-center justify-between gap-2 border-t border-[var(--chat-border)] pt-2">
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-[var(--chat-surface-muted)] px-2.5 py-1 text-[11px] font-medium text-[var(--chat-text-muted)]">
                  {config.mode === "images"
                    ? "文生图"
                    : config.mode === "edits"
                      ? "图生图"
                      : "对话调试"}
                </span>
                {config.mode !== "chat" && (
                  <span className="text-[11px] text-[var(--chat-text-muted)]">
                    {config.size} · {config.n} 张
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                {messages.length > 0 && (
                  <button
                    type="button"
                    onClick={clearMessages}
                    className="inline-flex items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-1.5 text-[12px] font-medium text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)]"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    清空
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => setPrompt("")}
                  className="inline-flex items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-1.5 text-[12px] font-medium text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)]"
                >
                  <X className="h-3.5 w-3.5" />
                  清空输入
                </button>
                <button
                  type="button"
                  onClick={() => void handleSend()}
                  className="inline-flex items-center gap-1.5 rounded-full bg-[var(--primary)] px-4 py-1.5 text-[13px] font-semibold text-[var(--primary-foreground)] transition hover:bg-[var(--primary)]/90"
                >
                  <SendHorizontal className="h-3.5 w-3.5" />
                  发送
                </button>
              </div>
            </div>
          </div>

          {/* 调试面板 */}
          {showDebug && (
            <div className="shrink-0 overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)]">
              <div className="flex items-center justify-between border-b border-[var(--chat-border)] px-4 py-2.5">
                <span className="inline-flex items-center gap-2 text-sm font-medium text-[var(--chat-text)]">
                  <Settings className="h-3.5 w-3.5 text-[var(--chat-text-muted)]" />
                  原始响应调试
                </span>
                <button
                  type="button"
                  onClick={() => setShowDebug(false)}
                  className="text-[12px] text-[var(--chat-text-muted)] transition hover:text-[var(--chat-text)]"
                >
                  收起
                </button>
              </div>
              <pre className="max-h-[240px] overflow-auto px-4 py-3 whitespace-pre-wrap font-mono text-[11px] leading-5 text-[var(--chat-text-soft)]">
                {toPrettyJson(debugPayload)}
              </pre>
            </div>
          )}

          {!showDebug && (
            <button
              type="button"
              onClick={() => setShowDebug(true)}
              className="inline-flex w-fit items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-1.5 text-[11px] font-medium text-[var(--chat-text-muted)] transition hover:text-[var(--chat-text)]"
            >
              <Settings className="h-3 w-3" />
              显示调试面板
            </button>
          )}
        </main>

        {/* ── 右栏：参考图 + 历史 ── */}
        <aside className="flex w-[300px] shrink-0 flex-col gap-4 overflow-y-auto">
          {/* 参考图（仅图生图模式） */}
          {config.mode === "edits" && (
            <div className="rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-4">
              <div className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-2 text-[var(--chat-text-muted)]">
                  <ImagePlus className="h-3.5 w-3.5" />
                  <span className="text-[11px] font-semibold tracking-wide">
                    参考图像
                  </span>
                </div>
                <span className="rounded-full bg-[var(--chat-surface-muted)] px-2 py-0.5 text-[10px] font-medium text-[var(--chat-text-muted)]">
                  {images.length} 张
                </span>
              </div>

              <label
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => {
                  event.preventDefault();
                  if (event.dataTransfer?.files?.length) {
                    void addFiles(event.dataTransfer.files);
                  }
                }}
                className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/80 px-4 py-6 text-center transition hover:border-[var(--primary)]/30 hover:bg-[var(--primary)]/5"
              >
                <UploadCloud className="h-5 w-5 text-[var(--chat-text-muted)]" />
                <div className="text-[12px] font-medium text-[var(--chat-text-soft)]">
                  点击、拖拽或粘贴图片
                </div>
                <div className="text-[11px] text-[var(--chat-text-muted)]">
                  PNG / JPG / WEBP
                </div>
                <input
                  type="file"
                  accept="image/*"
                  multiple
                  className="hidden"
                  onChange={(event) => {
                    if (event.target.files?.length) {
                      void addFiles(event.target.files);
                    }
                    event.target.value = "";
                  }}
                />
              </label>

              {images.length > 0 && (
                <div className="mt-3 grid grid-cols-3 gap-2">
                  {images.map((item, index) => (
                    <div
                      key={item.id}
                      className="group relative aspect-square overflow-hidden rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface-muted)]"
                    >
                      <img
                        src={item.objectUrl}
                        alt={`参考图 ${index + 1}`}
                        className="h-full w-full object-cover"
                      />
                      <div className="absolute left-1 top-1 rounded bg-black/50 px-1 py-0.5 text-[10px] font-medium text-white">
                        #{index + 1}
                      </div>
                      {item.maskDataUrl && (
                        <div className="absolute right-1 top-1 rounded bg-rose-500 px-1 py-0.5 text-[10px] font-medium text-white">
                          已涂抹
                        </div>
                      )}
                      <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 bg-black/50 opacity-0 transition group-hover:opacity-100">
                        <button
                          type="button"
                          onClick={() => openEditor(item.id)}
                          className="inline-flex items-center gap-1 rounded-full bg-[var(--primary)] px-2.5 py-1 text-[11px] font-medium text-white"
                        >
                          <Brush className="h-3 w-3" />
                          {item.maskDataUrl ? "修改" : "涂抹"}
                        </button>
                        <button
                          type="button"
                          onClick={() => removeImage(item.id)}
                          className="inline-flex items-center gap-1 rounded-full bg-rose-600 px-2.5 py-1 text-[11px] font-medium text-white"
                        >
                          <X className="h-3 w-3" />
                          移除
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

            </div>
          )}

          {/* 历史记录 */}
          <div className="flex-1 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-4">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2 text-[var(--chat-text-muted)]">
                <Clock className="h-3.5 w-3.5" />
                <span className="text-[11px] font-semibold tracking-wide">
                  历史记录
                </span>
              </div>
              <button
                type="button"
                onClick={() => void loadHistory(1, true)}
                className="inline-flex items-center gap-1 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)]"
              >
                <RefreshCcw
                  className={classNames(
                    "h-3 w-3",
                    historyLoading && "animate-spin"
                  )}
                />
                刷新
              </button>
            </div>

            {historyLoading && !historyBatches.length && (
              <div className="py-6 text-center text-[12px] text-[var(--chat-text-muted)]">
                加载中...
              </div>
            )}

            {!historyLoading &&
              !historyBatches.length &&
              !historyError && (
              <div className="py-6 text-center text-[12px] text-[var(--chat-text-muted)]">
                  暂无生成历史
              </div>
            )}

            {historyError && (
              <div className="rounded-lg border border-rose-100 bg-rose-50 px-3 py-2 text-[12px] text-rose-600">
                {historyError}
              </div>
            )}

            {historyBatches.length > 0 && (
              <div className="space-y-3">
                {historyBatches.map((batch) => (
                  <div
                    key={batch.requestId}
                    className="rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] p-3"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="text-[10px] text-[var(--chat-text-muted)]">
                          {formatHistoryTime(batch.createdAt)}
                        </div>
                        <div className="mt-0.5 truncate text-[12px] font-medium text-[var(--chat-text)]">
                          {batch.prompt}
                        </div>
                      </div>
                      <div className="flex shrink-0 flex-wrap gap-1">
                        <span className="rounded bg-[var(--chat-surface-muted)] px-1.5 py-0.5 text-[10px] text-[var(--chat-text-muted)]">
                          {batch.mode === "edits" ? "图生图" : "文生图"}
                        </span>
                      </div>
                    </div>

                    {batch.images.length > 0 && (
                      <div className="mt-2 grid grid-cols-2 gap-1.5">
                        {batch.images.slice(0, 4).map((item, index) => {
                          const previewUrl = resolvePreviewUrl(item);
                          const downloadUrl = resolveDownloadUrl(item);
                          return (
                            <div
                              key={`${batch.requestId}-${index}`}
                              className="relative aspect-square overflow-hidden rounded-md border border-[var(--chat-border)] bg-[var(--chat-surface-muted)]"
                            >
                              <img
                                src={previewUrl}
                                alt={item.fileName || `图${index + 1}`}
                                className="h-full w-full object-cover"
                              />
                              {downloadUrl && (
                                <a
                                  href={downloadUrl}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="absolute inset-0 flex items-center justify-center bg-black/0 transition hover:bg-black/40"
                                >
                                  <Download className="h-4 w-4 text-white opacity-0 transition hover:opacity-100" />
                                </a>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                ))}

                {historyBatches.length < historyTotal && (
                  <button
                    type="button"
                    onClick={() =>
                      void loadHistory(historyPageNo + 1, false)
                    }
                    disabled={historyLoadingMore}
                    className="inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-[12px] font-medium text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)] disabled:opacity-60"
                  >
                    <RefreshCcw
                      className={classNames(
                        "h-3 w-3",
                        historyLoadingMore && "animate-spin"
                      )}
                    />
                    {historyLoadingMore ? "加载中..." : "加载更多"}
                  </button>
                )}
              </div>
            )}
          </div>
        </aside>
      </section>

      {/* 图片涂抹编辑器 Overlay */}
      {editingImage && (
        <div className="fixed inset-0 z-40 flex flex-col bg-black/80">
          {/* 顶部工具栏 */}
          <div className="flex items-center justify-between gap-4 bg-black/40 px-6 py-3 backdrop-blur-sm">
            <div className="flex items-center gap-3">
              <span className="text-[14px] font-medium text-white/90">
                编辑 #{images.findIndex((i) => i.id === editingImage.id) + 1}
              </span>
              <span className="text-[12px] text-white/50">
                {editingImage.naturalWidth}×{editingImage.naturalHeight}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={clearCurrentMask}
                className="inline-flex items-center gap-1.5 rounded-lg bg-white/10 px-3 py-1.5 text-[13px] text-white/80 transition hover:bg-white/20"
              >
                <Trash2 className="h-3.5 w-3.5" />
                清除涂抹
              </button>
              <button
                type="button"
                onClick={closeEditor}
                className="inline-flex items-center gap-1.5 rounded-lg bg-[var(--primary)] px-4 py-1.5 text-[13px] font-medium text-white transition hover:bg-[var(--primary)]/90"
              >
                <Sparkles className="h-3.5 w-3.5" />
                完成编辑
              </button>
            </div>
          </div>

          {/* 中央画布区域 */}
          <div className="flex flex-1 items-center justify-center overflow-auto p-6">
            <div className="relative inline-block">
              <img
                ref={editorImageRef}
                src={editingImage.objectUrl}
                alt="编辑中"
                draggable={false}
                onLoad={refreshEditorLayout}
                className="block max-h-[70vh] max-w-full select-none rounded-lg shadow-2xl"
              />
              <canvas
                ref={maskCanvasRef}
                className="absolute inset-0 cursor-crosshair rounded-lg touch-none"
              />
            </div>
          </div>

          {/* 底部工具栏 */}
          <div className="flex items-center justify-center gap-4 bg-black/40 px-6 py-3 backdrop-blur-sm">
            <div className="inline-flex rounded-full bg-white/10 p-0.5">
              <button
                type="button"
                onClick={() => setToolMode("brush")}
                className={classNames(
                  "inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-[13px] font-medium transition",
                  toolMode === "brush"
                    ? "bg-white/20 text-white shadow-sm"
                    : "text-white/60 hover:text-white/90"
                )}
              >
                <Brush className="h-4 w-4" />
                笔刷
              </button>
              <button
                type="button"
                onClick={() => setToolMode("eraser")}
                className={classNames(
                  "inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-[13px] font-medium transition",
                  toolMode === "eraser"
                    ? "bg-white/20 text-white shadow-sm"
                    : "text-white/60 hover:text-white/90"
                )}
              >
                <Eraser className="h-4 w-4" />
                擦除
              </button>
            </div>

            <div className="inline-flex items-center gap-3 rounded-full bg-white/10 px-4 py-2 text-[13px] text-white/70">
              <span>笔刷大小</span>
              <input
                type="range"
                min={8}
                max={96}
                step={2}
                value={brushSize}
                onChange={(event) => setBrushSize(Number(event.target.value))}
                className="w-24 accent-white"
              />
              <span className="min-w-[2ch] font-mono text-white">{brushSize}</span>
            </div>
          </div>
        </div>
      )}

      {/* 图片预览弹窗 */}
      <ImagePreviewModal
        src={previewImage}
        onClose={() => setPreviewImage(null)}
      />
    </div>
  );
};

export default WorkspaceImageGeneration;
