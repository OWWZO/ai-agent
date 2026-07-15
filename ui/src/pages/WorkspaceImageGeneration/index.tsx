import { useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { Link } from "react-router-dom";
import classNames from "classnames";
import {
  ArrowLeft,
  ArrowUp,
  Brush,
  Download,
  Eraser,
  Paperclip,
  RefreshCcw,
  Settings,
  Sparkles,
  Trash2,
  UploadCloud,
  X,
} from "lucide-react";

import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";

import {
  checkerboardStyle,
  resolveDownloadUrl,
  resolvePreviewUrl,
} from "./utils";
import { useImageGenerationConfig } from "./useImageGenerationConfig";
import { useImageGenerationHistory } from "./useImageGenerationHistory";
import { useImageEditor } from "./useImageEditor";
import { useImageGenerationSession } from "./useImageGenerationSession";

interface WorkspaceImageGenerationProps {
  embedded?: boolean;
}

function ResultImageCard({
  url,
  label,
  downloadUrl,
  onPreview,
}: {
  url: string;
  label: string;
  downloadUrl?: string;
  onPreview: (url: string) => void;
}) {
  return (
    <div className="group relative aspect-square overflow-hidden rounded-2xl bg-[var(--chat-surface-soft)] shadow-[var(--shadow-sm)]">
      <button
        type="button"
        onClick={() => onPreview(url)}
        className="absolute inset-0 z-10 h-full w-full cursor-zoom-in"
        title="预览"
        aria-label={`预览 ${label}`}
      />
      <div className="h-full w-full" style={checkerboardStyle}>
        <img
          src={url}
          alt={label}
          className="pointer-events-none h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]"
        />
      </div>
      {downloadUrl ? (
        <div className="pointer-events-none absolute inset-x-0 bottom-0 z-20 flex justify-end p-2 opacity-0 transition group-hover:opacity-100">
          <a
            href={downloadUrl}
            target="_blank"
            rel="noreferrer"
            onClick={(event) => event.stopPropagation()}
            className="pointer-events-auto inline-flex h-8 w-8 items-center justify-center rounded-full bg-white/95 text-slate-800 shadow-sm"
            title="下载"
          >
            <Download className="h-3.5 w-3.5" />
          </a>
        </div>
      ) : null}
    </div>
  );
}

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
  } = useImageEditor();
  const {
    prompt,
    setPrompt,
    messages,
    clearMessages,
    handleSend,
    statusText,
    statusTone,
  } = useImageGenerationSession({
    config,
    collectEffectiveImages,
    buildMaskCompositeDataUrls,
    reloadHistory: () => loadHistory(1, true),
  });

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const isGenerating = messages.some(
    (m) => m.role === "assistant" && m.status === "loading"
  );

  const sessionImages = useMemo(
    () =>
      messages.flatMap((msg) =>
        msg.role === "assistant" && msg.status !== "loading"
          ? msg.images.map((img) => ({
            ...img,
            messageId: msg.id,
            prompt: "",
            createdAt: msg.timestamp,
          }))
          : []
      ),
    [messages]
  );

  const historyImages = useMemo(
    () =>
      historyBatches.flatMap((batch) =>
        batch.images
          .map((item, index) => {
            const previewUrl = resolvePreviewUrl(item);
            if (!previewUrl) return null;
            return {
              url: previewUrl,
              label: item.fileName || batch.prompt || `图片 ${index + 1}`,
              downloadUrl: resolveDownloadUrl(item) || undefined,
              messageId: batch.requestId,
              prompt: batch.prompt,
              createdAt: batch.createdAt,
              mode: batch.mode,
            };
          })
          .filter(Boolean) as Array<{
          url: string;
          label: string;
          downloadUrl?: string;
          messageId: string;
          prompt: string;
          createdAt?: string | null;
          mode?: string;
        }>
      ),
    [historyBatches]
  );

  const myImages = useMemo(() => {
    const seen = new Set<string>();
    const merged = [...sessionImages, ...historyImages];
    return merged.filter((item) => {
      if (seen.has(item.url)) return false;
      seen.add(item.url);
      return true;
    });
  }, [historyImages, sessionImages]);

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void handleSend();
    }
  };

  return (
    <div className="relative flex h-full flex-col bg-[var(--chat-bg)] text-[var(--chat-text)]">
      {/* top actions */}
      <div className="absolute right-4 top-4 z-20 flex items-center gap-2 sm:right-6 sm:top-5">
        {!embedded ? (
          <Link
            to="/"
            className="flex h-9 w-9 items-center justify-center rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-muted)] shadow-[var(--shadow-xs)] transition hover:text-[var(--chat-text)]"
            title="返回"
          >
            <ArrowLeft className="h-4 w-4" />
          </Link>
        ) : null}
        <button
          type="button"
          onClick={() => setShowSettings((v) => !v)}
          className={classNames(
            "flex h-9 w-9 items-center justify-center rounded-full border bg-[var(--chat-surface)] shadow-[var(--shadow-xs)] transition",
            showSettings
              ? "border-[var(--chat-accent)]/30 text-[var(--chat-accent)]"
              : "border-[var(--chat-border)] text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
          )}
          title="设置"
        >
          <Settings className="h-4 w-4" />
        </button>
        {!embedded ? (
          <div className="hidden lg:block">
            <WorkspaceToolSwitcher />
          </div>
        ) : null}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-[980px] px-5 pb-16 pt-14 sm:px-8 sm:pt-16">
          <h1 className="text-[40px] font-semibold tracking-tight text-[var(--chat-text)] sm:text-[44px]">
            图片
          </h1>

          {/* capsule composer */}
          <div className="mt-6">
            <div className="flex items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-2 py-1.5 shadow-[var(--shadow-md)]">
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                title="上传参考图"
              >
                <Paperclip className="h-4.5 w-4.5" />
              </button>
              <input
                ref={fileInputRef}
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
              <input
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={
                  images.length > 0 ? "描述如何修改这些图片..." : "描述新图片"
                }
                className="min-w-0 flex-1 border-none bg-transparent py-2.5 text-[15px] text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
              />
              <div className="flex shrink-0 items-center gap-1.5 pr-0.5">
                {messages.length > 0 ? (
                  <button
                    type="button"
                    onClick={clearMessages}
                    className="hidden h-9 items-center gap-1 rounded-full px-2.5 text-[12px] text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)] sm:inline-flex"
                    title="清空会话"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                ) : null}
                <button
                  type="button"
                  onClick={() => void handleSend()}
                  disabled={!prompt.trim() || isGenerating}
                  className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--primary)] text-[var(--primary-foreground)] transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-35"
                  title="生成"
                  aria-label="生成"
                >
                  {isGenerating ? (
                    <Sparkles className="h-4 w-4 animate-pulse" />
                  ) : (
                    <ArrowUp className="h-4 w-4" />
                  )}
                </button>
              </div>
            </div>

            {images.length > 0 ? (
              <div className="mt-3 flex flex-wrap items-center gap-2 px-1">
                <span className="rounded-full bg-[var(--chat-surface-soft)] px-2.5 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
                  图生图
                </span>
                <span className="text-[12px] text-[var(--chat-text-muted)]">
                  已上传 {images.length} 张参考图，将基于它们修改生成
                </span>
                {images.length > 1 ? (
                  <label className="ml-auto inline-flex items-center gap-1.5 text-[12px] text-[var(--chat-text-muted)]">
                    <input
                      type="checkbox"
                      checked={config.batchMode}
                      onChange={(event) =>
                        updateConfig("batchMode", event.target.checked)
                      }
                      className="h-3.5 w-3.5 rounded border-[var(--chat-border)]"
                    />
                    多图批处理
                  </label>
                ) : null}
              </div>
            ) : null}

            {statusText ? (
              <div
                className={classNames(
                  "mt-3 rounded-2xl px-3.5 py-2.5 text-[12px] font-medium",
                  statusTone === "success" &&
                    "bg-[var(--status-success-bg)] text-[var(--status-success-text)]",
                  statusTone === "error" &&
                    "bg-[var(--status-failed-bg)] text-[var(--status-failed-text)]",
                  statusTone === "default" &&
                    "bg-[var(--chat-surface-soft)] text-[var(--chat-text-muted)]"
                )}
              >
                {statusText}
              </div>
            ) : null}

            {images.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {images.map((item, index) => (
                  <div
                    key={item.id}
                    className="group relative h-16 w-16 overflow-hidden rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[var(--shadow-xs)]"
                  >
                    <img
                      src={item.objectUrl}
                      alt={`参考图 ${index + 1}`}
                      className="h-full w-full object-cover"
                    />
                    {item.maskDataUrl ? (
                      <div className="absolute left-1 top-1 rounded bg-rose-500 px-1 text-[9px] text-white">
                        已涂抹
                      </div>
                    ) : null}
                    <div className="absolute inset-0 flex flex-col items-center justify-center gap-1 bg-black/50 opacity-0 transition group-hover:opacity-100">
                      <button
                        type="button"
                        onClick={() => openEditor(item.id)}
                        className="rounded-full bg-white px-2 py-0.5 text-[10px] font-medium text-slate-800"
                      >
                        涂抹
                      </button>
                      <button
                        type="button"
                        onClick={() => removeImage(item.id)}
                        className="rounded-full bg-rose-500 px-2 py-0.5 text-[10px] font-medium text-white"
                      >
                        移除
                      </button>
                    </div>
                  </div>
                ))}
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="flex h-16 w-16 flex-col items-center justify-center gap-1 rounded-2xl border border-dashed border-[var(--chat-border)] text-[var(--chat-text-muted)] transition hover:border-[var(--chat-border-strong)] hover:bg-[var(--chat-surface-soft)]"
                >
                  <UploadCloud className="h-4 w-4" />
                  <span className="text-[10px]">添加</span>
                </button>
              </div>
            ) : null}
          </div>

          {/* my images：无图片时整块不展示 */}
          {myImages.length > 0 || isGenerating ? (
            <section className="mt-12">
              <div className="mb-4 flex items-center justify-between gap-3">
                <h2 className="text-[18px] font-semibold tracking-tight text-[var(--chat-text)]">
                  我的图片
                </h2>
                <button
                  type="button"
                  onClick={() => void loadHistory(1, true)}
                  className="inline-flex items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-1.5 text-[12px] font-medium text-[var(--chat-text-muted)] transition hover:text-[var(--chat-text)]"
                >
                  <RefreshCcw
                    className={classNames("h-3.5 w-3.5", historyLoading && "animate-spin")}
                  />
                  刷新
                </button>
              </div>

              {isGenerating ? (
                <div className="mb-4 flex items-center gap-2 rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-4 text-[13px] text-[var(--chat-text-muted)]">
                  <Sparkles className="h-4 w-4 animate-pulse text-[var(--chat-accent)]" />
                  正在生成图像...
                </div>
              ) : null}

              {historyError ? (
                <div className="mb-4 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] text-rose-600">
                  {historyError}
                </div>
              ) : null}

              {myImages.length > 0 ? (
                <>
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
                    {myImages.map((img, index) => (
                      <ResultImageCard
                        key={`${img.messageId}-${img.url}-${index}`}
                        url={img.url}
                        label={img.label}
                        downloadUrl={img.downloadUrl}
                        onPreview={setPreviewImage}
                      />
                    ))}
                  </div>
                  {historyBatches.length < historyTotal ? (
                    <button
                      type="button"
                      onClick={() => void loadHistory(historyPageNo + 1, false)}
                      disabled={historyLoadingMore}
                      className="mt-5 inline-flex w-full items-center justify-center gap-1.5 rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2.5 text-[13px] font-medium text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)] disabled:opacity-60"
                    >
                      <RefreshCcw
                        className={classNames(
                          "h-3.5 w-3.5",
                          historyLoadingMore && "animate-spin"
                        )}
                      />
                      {historyLoadingMore ? "加载中..." : "加载更多"}
                    </button>
                  ) : null}
                </>
              ) : null}
            </section>
          ) : null}
        </div>
      </div>

      {/* settings panel */}
      {showSettings ? (
        <>
          <button
            type="button"
            aria-label="关闭设置"
            className="fixed inset-0 z-30 bg-[oklch(0.2_0.01_60/0.14)]"
            onClick={() => setShowSettings(false)}
          />
          <aside className="fixed right-0 top-0 z-40 flex h-full w-full max-w-[340px] flex-col border-l border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[var(--shadow-xl)]">
            <div className="flex items-center justify-between border-b border-[var(--chat-border)] px-4 py-4">
              <div>
                <div className="text-[14px] font-semibold text-[var(--chat-text)]">生成设置</div>
                <div className="mt-0.5 text-[12px] text-[var(--chat-text-muted)]">
                  模型与输出参数
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowSettings(false)}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--chat-text-muted)] hover:bg-[var(--chat-surface-soft)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="space-y-4 overflow-y-auto p-4">
              <label className="block">
                <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                  Base URL
                </span>
                <input
                  value={config.baseUrl}
                  onChange={(event) => updateConfig("baseUrl", event.target.value)}
                  placeholder="https://..."
                  className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-2 text-sm text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/30"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                  API Key
                </span>
                <input
                  type="password"
                  value={config.apiKey}
                  onChange={(event) => updateConfig("apiKey", event.target.value)}
                  placeholder="sk-..."
                  className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-2 font-mono text-sm tracking-wide text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/30"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                  Model
                </span>
                <input
                  value={config.model}
                  onChange={(event) => updateConfig("model", event.target.value)}
                  className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-2 font-mono text-sm text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/30"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                  Size
                </span>
                <input
                  value={config.size}
                  onChange={(event) => updateConfig("size", event.target.value)}
                  placeholder="1024x1024"
                  className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-2 text-sm text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/30"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-[11px] font-medium text-[var(--chat-text-muted)]">
                  数量 n
                </span>
                <input
                  type="number"
                  min={1}
                  max={8}
                  value={config.n}
                  onChange={(event) => updateConfig("n", Number(event.target.value) || 1)}
                  className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-2 text-sm text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/30"
                />
              </label>
            </div>
          </aside>
        </>
      ) : null}

      {/* mask editor overlay */}
      {editingImage && (
        <div className="fixed inset-0 z-40 flex flex-col bg-black/80">
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

      <ImagePreviewModal src={previewImage} onClose={() => setPreviewImage(null)} />
    </div>
  );
};

export default WorkspaceImageGeneration;
