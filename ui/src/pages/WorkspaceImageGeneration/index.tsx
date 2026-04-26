import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import classNames from "classnames";
import {
  ArrowLeft,
  Brush,
  Code2,
  Download,
  Eraser,
  ImagePlus,
  RefreshCcw,
  SendHorizontal,
  Sparkles,
  Trash2,
  UploadCloud,
  WandSparkles,
  X,
} from "lucide-react";

import {
  ImageGenerationRequestError,
  requestDirectChat,
  requestImageGenerationHistory,
  requestImageGenerationTool,
} from "@/services/imageGeneration";
import WorkspaceToolSwitcher from "@/components/WorkspaceToolSwitcher";

import type {
  AssistantMessage,
  DecodeResult,
  EditorImageItem,
  GenerationConfig,
  GenerationMessage,
  ImageGenerationHistoryBatch,
  RequestMode,
  ResultImageItem,
  UserMessage,
  WorkspaceTab,
} from "./types";
import {
  IMAGE_GENERATION_STORAGE_KEY,
  buildMaskedComposite,
  checkerboardStyle,
  createLocalId,
  downloadDataUrl,
  fileToDataUrl,
  formatBytes,
  formatHistoryTime,
  hasCanvasDrawing,
  loadImageElement,
  normalizeToDataUrl,
  resolveDownloadUrl,
  resolveImageNaturalSize,
  resolvePreviewUrl,
  toPrettyJson,
  trimTrailingSlash,
} from "./utils";

type StatusTone = "default" | "success" | "error";
const HISTORY_PAGE_SIZE = 10;

const createDefaultConfig = (): GenerationConfig => ({
  baseUrl: "https://www.openclaudecode.cn",
  apiKey: "",
  model: "gpt-image-2",
  mode: "images",
  size: "1024x1024",
  n: 1,
});

const loadStoredConfig = (): GenerationConfig => {
  const defaults = createDefaultConfig();
  try {
    const raw = localStorage.getItem(IMAGE_GENERATION_STORAGE_KEY);
    if (!raw) {
      return defaults;
    }
    const parsed = JSON.parse(raw) as Partial<GenerationConfig>;
    return {
      ...defaults,
      ...parsed,
      mode: (parsed.mode as RequestMode) || defaults.mode,
      n: Math.max(1, Math.min(10, Number(parsed.n) || defaults.n)),
    };
  } catch {
    return defaults;
  }
};

interface WorkspaceImageGenerationProps {
  embedded?: boolean;
}

const WorkspaceImageGeneration: ReactorType.FC<WorkspaceImageGenerationProps> = ({ embedded }) => {
  const [activeTab, setActiveTab] = useState<WorkspaceTab>("generate");
  const [config, setConfig] = useState<GenerationConfig>(() => loadStoredConfig());
  const [statusText, setStatusText] = useState("");
  const [statusTone, setStatusTone] = useState<StatusTone>("default");
  const [prompt, setPrompt] = useState("");
  const [messages, setMessages] = useState<GenerationMessage[]>([]);
  const [debugPayload, setDebugPayload] = useState<unknown>("（尚未请求）");
  const [decodeInput, setDecodeInput] = useState("");
  const [decodeResult, setDecodeResult] = useState<DecodeResult | null>(null);
  const [decodeStatus, setDecodeStatus] = useState("");
  const [decodeStatusTone, setDecodeStatusTone] = useState<StatusTone>("default");
  const [images, setImages] = useState<EditorImageItem[]>([]);
  const [editingImageId, setEditingImageId] = useState<string | null>(null);
  const [brushSize, setBrushSize] = useState(32);
  const [toolMode, setToolMode] = useState<"brush" | "eraser">("brush");
  const [historyBatches, setHistoryBatches] = useState<ImageGenerationHistoryBatch[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPageNo, setHistoryPageNo] = useState(1);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyLoadingMore, setHistoryLoadingMore] = useState(false);
  const [historyError, setHistoryError] = useState("");

  const chatRef = useRef<HTMLDivElement>(null);
  const editorImageRef = useRef<HTMLImageElement>(null);
  const maskCanvasRef = useRef<HTMLCanvasElement>(null);
  const maskContextRef = useRef<CanvasRenderingContext2D | null>(null);
  const isDrawingRef = useRef(false);
  const lastPointRef = useRef<{ x: number; y: number } | null>(null);
  const imagesRef = useRef<EditorImageItem[]>([]);

  const editingImage = images.find((item) => item.id === editingImageId) || null;

  useEffect(() => {
    localStorage.setItem(IMAGE_GENERATION_STORAGE_KEY, JSON.stringify(config));
  }, [config]);

  useEffect(() => {
    imagesRef.current = images;
  }, [images]);

  useEffect(() => {
    return () => {
      imagesRef.current.forEach((item) => URL.revokeObjectURL(item.objectUrl));
    };
  }, []);

  useEffect(() => {
    if (!chatRef.current) {
      return;
    }
    chatRef.current.scrollTo({
      top: chatRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [messages]);

  useEffect(() => {
    if (config.mode !== "edits") {
      return;
    }

    const handlePaste = (event: ClipboardEvent) => {
      const clipboardItems = event.clipboardData?.items;
      if (!clipboardItems?.length) {
        return;
      }

      const pastedImages: File[] = [];
      Array.from(clipboardItems).forEach((item) => {
        if (item.type.startsWith("image/")) {
          const file = item.getAsFile();
          if (file) {
            pastedImages.push(file);
          }
        }
      });

      if (!pastedImages.length) {
        return;
      }

      event.preventDefault();
      void addFiles(pastedImages);
    };

    document.addEventListener("paste", handlePaste);
    return () => document.removeEventListener("paste", handlePaste);
  }, [config.mode]);

  useEffect(() => {
    if (!editingImage) {
      return;
    }

    let cancelled = false;

    // 让画布始终跟随图片当前显示尺寸，避免窗口缩放后蒙版错位。
    const syncCanvas = async () => {
      const imageElement = editorImageRef.current;
      const canvas = maskCanvasRef.current;
      if (!imageElement || !canvas || cancelled) {
        return;
      }

      const width = imageElement.clientWidth;
      const height = imageElement.clientHeight;
      if (!width || !height) {
        return;
      }

      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext("2d");
      if (!context) {
        return;
      }

      context.clearRect(0, 0, width, height);
      context.lineCap = "round";
      context.lineJoin = "round";
      maskContextRef.current = context;

      if (editingImage.maskDataUrl) {
        try {
          const maskImage = await loadImageElement(editingImage.maskDataUrl);
          if (!cancelled) {
            context.drawImage(maskImage, 0, 0, width, height);
          }
        } catch {
          // 旧蒙版加载失败时忽略，避免卡住后续编辑。
        }
      }
    };

    const handleResize = () => {
      void syncCanvas();
    };

    window.addEventListener("resize", handleResize);
    void syncCanvas();
    return () => {
      cancelled = true;
      window.removeEventListener("resize", handleResize);
    };
  }, [editingImage]);

  useEffect(() => {
    const canvas = maskCanvasRef.current;
    if (!canvas || !editingImage) {
      return;
    }

    const getPoint = (event: MouseEvent | TouchEvent) => {
      const rect = canvas.getBoundingClientRect();
      const source =
        "touches" in event ? event.touches[0] : event;
      return {
        x: source.clientX - rect.left,
        y: source.clientY - rect.top,
      };
    };

    const drawDot = (x: number, y: number) => {
      const context = maskContextRef.current;
      if (!context) {
        return;
      }
      context.globalCompositeOperation = toolMode === "eraser" ? "destination-out" : "source-over";
      context.fillStyle = "rgba(239, 68, 68, 0.55)";
      context.beginPath();
      context.arc(x, y, brushSize / 2, 0, Math.PI * 2);
      context.fill();
    };

    const drawSegment = (startX: number, startY: number, endX: number, endY: number) => {
      const context = maskContextRef.current;
      if (!context) {
        return;
      }
      context.globalCompositeOperation = toolMode === "eraser" ? "destination-out" : "source-over";
      context.strokeStyle = "rgba(239, 68, 68, 0.55)";
      context.lineWidth = brushSize;
      context.beginPath();
      context.moveTo(startX, startY);
      context.lineTo(endX, endY);
      context.stroke();
    };

    const handleStart = (event: MouseEvent | TouchEvent) => {
      event.preventDefault();
      isDrawingRef.current = true;
      const point = getPoint(event);
      lastPointRef.current = point;
      drawDot(point.x, point.y);
    };

    const handleMove = (event: MouseEvent | TouchEvent) => {
      if (!isDrawingRef.current || !lastPointRef.current) {
        return;
      }
      event.preventDefault();
      const point = getPoint(event);
      drawSegment(lastPointRef.current.x, lastPointRef.current.y, point.x, point.y);
      drawDot(point.x, point.y);
      lastPointRef.current = point;
    };

    const handleEnd = () => {
      isDrawingRef.current = false;
      lastPointRef.current = null;
    };

    canvas.addEventListener("mousedown", handleStart as EventListener);
    window.addEventListener("mousemove", handleMove as EventListener);
    window.addEventListener("mouseup", handleEnd);
    canvas.addEventListener("touchstart", handleStart as EventListener, { passive: false });
    window.addEventListener("touchmove", handleMove as EventListener, { passive: false });
    window.addEventListener("touchend", handleEnd);

    return () => {
      canvas.removeEventListener("mousedown", handleStart as EventListener);
      window.removeEventListener("mousemove", handleMove as EventListener);
      window.removeEventListener("mouseup", handleEnd);
      canvas.removeEventListener("touchstart", handleStart as EventListener);
      window.removeEventListener("touchmove", handleMove as EventListener);
      window.removeEventListener("touchend", handleEnd);
    };
  }, [brushSize, editingImage, toolMode]);

  const setStatus = (text: string, tone: StatusTone = "default") => {
    setStatusText(text);
    setStatusTone(tone);
  };

  const setDecodeNotice = (text: string, tone: StatusTone = "default") => {
    setDecodeStatus(text);
    setDecodeStatusTone(tone);
  };

  const updateConfig = <K extends keyof GenerationConfig>(key: K, value: GenerationConfig[K]) => {
    setConfig((previous) => ({
      ...previous,
      [key]: value,
    }));
  };

  const loadHistory = async (pageNo = 1, replace = true) => {
    if (replace) {
      setHistoryLoading(true);
    } else {
      setHistoryLoadingMore(true);
    }
    setHistoryError("");
    try {
      const page = await requestImageGenerationHistory({
        pageNo,
        pageSize: HISTORY_PAGE_SIZE,
      });
      setHistoryTotal(page.total || 0);
      setHistoryPageNo(pageNo);
      setHistoryBatches((previous) =>
        replace ? page.list || [] : [...previous, ...(page.list || [])]
      );
    } catch (error) {
      setHistoryError(error instanceof Error ? error.message : "历史查询失败");
    } finally {
      setHistoryLoading(false);
      setHistoryLoadingMore(false);
    }
  };

  useEffect(() => {
    void loadHistory(1, true);
  }, []);

  const addFiles = async (fileList: FileList | File[]) => {
    const selectedFiles = Array.from(fileList).filter((file) => file.type.startsWith("image/"));
    if (!selectedFiles.length) {
      return;
    }

    const nextItems = await Promise.all(
      selectedFiles.map(async (file) => {
        const objectUrl = URL.createObjectURL(file);
        try {
          const size = await resolveImageNaturalSize(objectUrl);
          return {
            id: createLocalId("img"),
            file,
            objectUrl,
            naturalWidth: size.width,
            naturalHeight: size.height,
            maskDataUrl: null,
          } satisfies EditorImageItem;
        } catch {
          return {
            id: createLocalId("img"),
            file,
            objectUrl,
            naturalWidth: 0,
            naturalHeight: 0,
            maskDataUrl: null,
          } satisfies EditorImageItem;
        }
      })
    );

    setImages((previous) => [...previous, ...nextItems]);
  };

  const collectEffectiveImages = () => {
    if (!editingImageId || !maskCanvasRef.current) {
      return images;
    }

    const currentImage = images.find((item) => item.id === editingImageId);
    if (!currentImage) {
      return images;
    }

    const sourceCanvas = maskCanvasRef.current;
    const naturalWidth =
      currentImage.naturalWidth || editorImageRef.current?.naturalWidth || sourceCanvas.width;
    const naturalHeight =
      currentImage.naturalHeight || editorImageRef.current?.naturalHeight || sourceCanvas.height;

    const outputCanvas = document.createElement("canvas");
    outputCanvas.width = naturalWidth;
    outputCanvas.height = naturalHeight;

    const outputContext = outputCanvas.getContext("2d");
    if (!outputContext) {
      return images;
    }

    outputContext.drawImage(sourceCanvas, 0, 0, naturalWidth, naturalHeight);
    const nextMaskDataUrl = hasCanvasDrawing(outputCanvas)
      ? outputCanvas.toDataURL("image/png")
      : null;

    const nextImages = images.map((item) =>
      item.id === editingImageId
        ? {
            ...item,
            naturalWidth,
            naturalHeight,
            maskDataUrl: nextMaskDataUrl,
          }
        : item
    );
    setImages(nextImages);
    return nextImages;
  };

  const closeEditor = () => {
    collectEffectiveImages();
    setEditingImageId(null);
  };

  const openEditor = (imageId: string) => {
    collectEffectiveImages();
    setEditingImageId(imageId);
  };

  const removeImage = (imageId: string) => {
    setImages((previous) => {
      const target = previous.find((item) => item.id === imageId);
      if (target) {
        URL.revokeObjectURL(target.objectUrl);
      }
      return previous.filter((item) => item.id !== imageId);
    });
    if (editingImageId === imageId) {
      setEditingImageId(null);
    }
  };

  const clearCurrentMask = () => {
    if (!maskCanvasRef.current) {
      return;
    }
    const context = maskCanvasRef.current.getContext("2d");
    if (context) {
      context.clearRect(0, 0, maskCanvasRef.current.width, maskCanvasRef.current.height);
    }
    if (editingImageId) {
      setImages((previous) =>
        previous.map((item) =>
          item.id === editingImageId
            ? {
                ...item,
                maskDataUrl: null,
              }
            : item
        )
      );
    }
  };

  const clearMessages = () => {
    setMessages([]);
    setStatus("", "default");
    setDebugPayload("（尚未请求）");
  };

  const handleDecode = () => {
    try {
      const result = normalizeToDataUrl(decodeInput);
      setDecodeResult(result);
      setDecodeNotice("解析成功", "success");
    } catch (error) {
      setDecodeResult(null);
      setDecodeNotice(
        error instanceof Error ? error.message : "解析失败",
        "error"
      );
    }
  };

  const createAssistantMessage = (assistantId: string): AssistantMessage => ({
    id: assistantId,
    role: "assistant",
    status: "loading",
    summary: "正在生成图像...",
    images: [],
    timestamp: Date.now(),
  });

  const updateAssistantMessage = (
    assistantId: string,
    updater: (previous: AssistantMessage) => AssistantMessage
  ) => {
    setMessages((previous) =>
      previous.map((item) => {
        if (item.role !== "assistant" || item.id !== assistantId) {
          return item;
        }
        return updater(item);
      })
    );
  };

  const buildOutputName = (text: string) => {
    const normalized = text
      .trim()
      .slice(0, 16)
      .replace(/[<>:"/\\|?*\x00-\x1F]/g, "_");
    return normalized || "图片生成结果";
  };

  const handleSend = async () => {
    const currentPrompt = prompt.trim();

    if (!currentPrompt) {
      setStatus("请先输入 Prompt", "error");
      return;
    }

    const effectiveImages = collectEffectiveImages();
    if (config.mode === "edits" && !effectiveImages.length) {
      setStatus("请先上传至少一张参考图片", "error");
      return;
    }

    const userMessage: UserMessage = {
      id: createLocalId("user"),
      role: "user",
      prompt: currentPrompt,
      mode: config.mode,
      images: config.mode === "edits" ? effectiveImages.map((item) => item.objectUrl) : [],
      timestamp: Date.now(),
    };
    const assistantId = createLocalId("assistant");

    setMessages((previous) => [
      ...previous,
      userMessage,
      createAssistantMessage(assistantId),
    ]);
    setPrompt("");
    setStatus("请求发送中...", "default");
    setDebugPayload("请求发送中...");

    try {
      if (config.mode === "chat") {
        const currentBaseUrl = trimTrailingSlash(config.baseUrl);
        if (!currentBaseUrl || !config.apiKey.trim() || !config.model.trim()) {
          setStatus("请填写完整的对话调试配置与 Prompt", "error");
          return;
        }
        const chatResult = await requestDirectChat({
          baseUrl: currentBaseUrl,
          apiKey: config.apiKey.trim(),
          model: config.model.trim(),
          prompt: currentPrompt,
        });

        setDebugPayload(chatResult.rawResponse);
        const outputImages: ResultImageItem[] = [];
        if (chatResult.image?.dataUrl) {
          outputImages.push({
            url: chatResult.image.dataUrl,
            label: "对话返回图片",
          });
        } else if (chatResult.image?.url) {
          outputImages.push({
            url: chatResult.image.url,
            label: "对话返回图片",
            downloadUrl: chatResult.image.url,
          });
        }

        updateAssistantMessage(assistantId, () => ({
          id: assistantId,
          role: "assistant",
          status: outputImages.length || chatResult.text ? "done" : "error",
          summary: outputImages.length
            ? "对话接口返回了图片结果"
            : chatResult.text || "响应中未识别到图片内容",
          text: chatResult.text || undefined,
          images: outputImages,
          rawResponse: chatResult.rawResponse,
          timestamp: Date.now(),
        }));
        setStatus(outputImages.length ? "生成完成" : "未识别到图片内容", outputImages.length ? "success" : "error");
        return;
      }

      const sourceImageDataUrls = await Promise.all(
        effectiveImages.map((item) => fileToDataUrl(item.file))
      );
      const maskFileNames: string[] = [];
      for (let index = 0; index < effectiveImages.length; index += 1) {
        const currentImage = effectiveImages[index];
        if (currentImage.maskDataUrl) {
          const composite = await buildMaskedComposite({
            imageSrc: sourceImageDataUrls[index],
            maskDataUrl: currentImage.maskDataUrl,
            width: currentImage.naturalWidth,
            height: currentImage.naturalHeight,
          });
          maskFileNames.push(composite);
        } else {
          maskFileNames.push("");
        }
      }

      const toolResponse = await requestImageGenerationTool({
        requestId: createLocalId("image"),
        prompt: currentPrompt,
        mode: config.mode,
        size: config.size.trim(),
        n: config.n,
        fileNames: config.mode === "edits" ? sourceImageDataUrls : [],
        maskFileNames: config.mode === "edits" ? maskFileNames : [],
        fileName: buildOutputName(currentPrompt),
        fileDescription: currentPrompt.slice(0, 80),
      });

      setDebugPayload(toolResponse.rawResponse ?? toolResponse);

      const outputImages: ResultImageItem[] = [];
      (toolResponse.fileInfo || []).forEach((item, index) => {
        const previewUrl = resolvePreviewUrl(item);
        if (!previewUrl) {
          return;
        }
        outputImages.push({
          url: previewUrl,
          label: item.fileName || `结果图 ${index + 1}`,
          downloadUrl: resolveDownloadUrl(item),
        });
      });

      updateAssistantMessage(assistantId, () => ({
        id: assistantId,
        role: "assistant",
        status: outputImages.length ? "done" : "error",
        summary: toolResponse.data,
        images: outputImages,
        rawResponse: toolResponse.rawResponse ?? toolResponse,
        timestamp: Date.now(),
      }));
      setStatus(
        toolResponse.usedFallback ? "生成完成（已自动切换兼容接口）" : "生成完成",
        "success"
      );
      void loadHistory(1, true);
    } catch (error) {
      const requestError =
        error instanceof ImageGenerationRequestError
          ? error
          : new ImageGenerationRequestError(
              error instanceof Error ? error.message : "请求失败"
            );
      setDebugPayload(requestError.rawResponse ?? requestError.message);
      updateAssistantMessage(assistantId, () => ({
        id: assistantId,
        role: "assistant",
        status: "error",
        summary: "请求失败",
        images: [],
        error: requestError.message,
        rawResponse: requestError.rawResponse,
        timestamp: Date.now(),
      }));
      setStatus("请求失败", "error");
    }
  };

  return (
    <div className="flex h-full flex-col bg-[var(--page-gradient)] text-[var(--chat-text)]">
      {/* Header */}
      <div className="shrink-0 border-b border-[var(--chat-border)] bg-[var(--chat-surface)]/80 px-5 py-3 backdrop-blur-md">
        <div className="mx-auto flex max-w-[1280px] flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            {!embedded && (
              <Link
                to="/"
                className="flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--chat-border)] text-[var(--chat-text-muted)] transition hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
              >
                <ArrowLeft className="h-4 w-4" />
              </Link>
            )}
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--primary)]/10 text-[var(--primary)]">
              <WandSparkles className="h-4.5 w-4.5" />
            </div>
            <div>
              <h1 className="text-[15px] font-semibold tracking-tight text-[var(--chat-text)]">
                米醋画图
              </h1>
              <p className="text-[12px] text-[var(--chat-text-muted)]">
                AI 图像生成工作台
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {!embedded && <WorkspaceToolSwitcher />}
            <div className="inline-flex rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-0.5">
                {([
                  { key: "decode", label: "Base64 解析", icon: Code2 },
                  { key: "generate", label: "API 生成", icon: Sparkles },
                ] as const).map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setActiveTab(item.key)}
                    className={classNames(
                      "inline-flex flex-1 items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition sm:flex-none",
                      activeTab === item.key
                        ? "bg-[var(--chat-surface)] text-[var(--chat-text)] shadow-sm"
                        : "text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
                    )}
                  >
                    <item.icon className="h-4 w-4" />
                    <span>{item.label}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {activeTab === "decode" ? (
          <section className="workspace-fade-enter mx-auto w-full max-w-[980px] p-4">
            <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-5 shadow-sm sm:p-8">
              <div className="mb-6 flex flex-col gap-2">
                <div className="inline-flex w-fit items-center gap-2 rounded-full border border-[var(--primary)]/15 bg-[var(--primary)]/8 px-3 py-1 text-[12px] font-medium text-[var(--primary)]">
                  <Code2 className="h-3.5 w-3.5" />
                  <span>Base64 预览与下载</span>
                </div>
                <h2 className="text-[22px] font-semibold tracking-tight text-[var(--chat-text)]">
                  粘贴 Base64 编码或 Data URL
                </h2>
                <p className="text-sm leading-6 text-[var(--chat-text-muted)]">
                  会自动识别纯 Base64 和 `data:image/...;base64,...` 两种格式，方便快速校验图片内容。
                </p>
              </div>

              <textarea
                value={decodeInput}
                onChange={(event) => setDecodeInput(event.target.value)}
                placeholder="把 Base64 内容粘贴到这里..."
                className="min-h-[240px] w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-5 py-4 font-mono text-[13px] leading-6 text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:bg-[var(--chat-surface)] focus:ring-2 focus:ring-[var(--primary)]/10"
              />

              <div className="mt-4 flex flex-col gap-3 sm:flex-row">
                <button
                  type="button"
                  onClick={handleDecode}
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-[var(--primary)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--primary)]/90"
                >
                  <RefreshCcw className="h-4 w-4" />
                  <span>解析预览</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setDecodeInput("");
                    setDecodeResult(null);
                    setDecodeNotice("", "default");
                  }}
                  className="inline-flex items-center justify-center gap-2 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-5 py-3 text-sm font-semibold text-[var(--chat-text-soft)] transition hover:border-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
                >
                  <Trash2 className="h-4 w-4" />
                  <span>清空</span>
                </button>
              </div>

              {decodeStatus ? (
                <div
                  className={classNames(
                    "mt-4 text-sm font-medium",
                    decodeStatusTone === "success" && "text-emerald-600",
                    decodeStatusTone === "error" && "text-rose-600",
                    decodeStatusTone === "default" && "text-[var(--chat-text-muted)]"
                  )}
                >
                  {decodeStatus}
                </div>
              ) : null}

              {decodeResult ? (
                <div className="mt-6 grid gap-4 lg:grid-cols-[1.15fr_0.85fr]">
                  <div
                    className="overflow-hidden rounded-xl border border-[var(--chat-border)] p-4"
                    style={checkerboardStyle}
                  >
                    <img
                      src={decodeResult.dataUrl}
                      alt="Base64 preview"
                      className="mx-auto max-h-[420px] w-full rounded-lg object-contain"
                    />
                  </div>

                  <div className="flex flex-col gap-3">
                    <div className="rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-5">
                      <div className="mb-3 text-xs font-semibold uppercase tracking-[0.18em] text-[var(--chat-text-muted)]">
                        图片信息
                      </div>
                      <div className="space-y-3 text-sm text-[var(--chat-text-soft)]">
                        <div>
                          <div className="text-[11px] uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">Mime</div>
                          <div className="mt-1 font-mono text-[13px] text-[var(--chat-text)]">{decodeResult.mimeType}</div>
                        </div>
                        <div>
                          <div className="text-[11px] uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">体积估算</div>
                          <div className="mt-1 font-mono text-[13px] text-[var(--chat-text)]">{formatBytes(decodeResult.byteLength)}</div>
                        </div>
                        <div>
                          <div className="text-[11px] uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">Base64 长度</div>
                          <div className="mt-1 font-mono text-[13px] text-[var(--chat-text)]">{decodeResult.base64Length}</div>
                        </div>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() =>
                        downloadDataUrl(
                          decodeResult.dataUrl,
                          `base64-preview.${decodeResult.fileExtension}`
                        )
                      }
                      className="inline-flex items-center justify-center gap-2 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-5 py-3 text-sm font-semibold text-[var(--chat-text-soft)] transition hover:border-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
                    >
                      <Download className="h-4 w-4" />
                      <span>下载图片</span>
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          </section>
        ) : (
          <section className="workspace-fade-enter grid gap-6 xl:grid-cols-[420px_minmax(0,1fr)]">
            <div className="space-y-4">
              <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-5 shadow-sm sm:p-6">
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <div className="text-[11px] font-semibold uppercase tracking-[0.24em] text-[var(--chat-text-muted)]">
                      Workspace Config
                    </div>
                    <h2 className="mt-1 text-lg font-semibold tracking-tight text-[var(--chat-text)]">
                      工作台参数
                    </h2>
                  </div>
                  <div className="rounded-full bg-[var(--chat-surface-muted)] px-3 py-1 text-[11px] font-medium text-[var(--chat-text-muted)]">
                    {config.mode === "chat" ? "Direct API" : "Java Backend"}
                  </div>
                </div>

                <div className="space-y-3">
                  <label className="block">
                    <span className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">
                      Endpoint 模式
                    </span>
                    <select
                      value={config.mode}
                      onChange={(event) => updateConfig("mode", event.target.value as RequestMode)}
                      className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2.5 text-sm text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                    >
                      <option value="images">文生图</option>
                      <option value="edits">图生图</option>
                      <option value="chat">对话调试</option>
                    </select>
                  </label>

                  {config.mode === "chat" ? (
                    <>
                      <label className="block">
                        <span className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">
                          Base URL
                        </span>
                        <input
                          value={config.baseUrl}
                          onChange={(event) => updateConfig("baseUrl", event.target.value)}
                          placeholder="https://..."
                          className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2.5 text-sm text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                        />
                      </label>

                      <label className="block">
                        <span className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">
                          API Key
                        </span>
                        <input
                          type="password"
                          value={config.apiKey}
                          onChange={(event) => updateConfig("apiKey", event.target.value)}
                          placeholder="sk-..."
                          className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2.5 text-sm font-mono tracking-wide text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                        />
                      </label>

                      <label className="block">
                        <span className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">
                          Model
                        </span>
                        <input
                          value={config.model}
                          onChange={(event) => updateConfig("model", event.target.value)}
                          className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2.5 text-sm font-mono text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                        />
                      </label>
                    </>
                  ) : (
                    <div className="rounded-xl border border-sky-100 bg-sky-50/80 px-4 py-3 text-sm leading-6 text-sky-700">
                      文生图与图生图现在统一走 Java 后端，再由 Java 代理到 Python 生图服务。前端不再直连工具端。
                    </div>
                  )}

                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block">
                      <span className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">
                        Size
                      </span>
                      <input
                        value={config.size}
                        onChange={(event) => updateConfig("size", event.target.value)}
                        className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2.5 text-sm font-mono text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                      />
                    </label>

                    <label className="block">
                      <span className="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-[var(--chat-text-muted)]">
                        N (张数)
                      </span>
                      <input
                        type="number"
                        min={1}
                        max={10}
                        value={config.n}
                        onChange={(event) =>
                          updateConfig("n", Math.max(1, Math.min(10, Number(event.target.value) || 1)))
                        }
                        className="w-full rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2.5 text-sm font-mono text-[var(--chat-text)] outline-none transition focus:border-[var(--primary)]/40 focus:ring-2 focus:ring-[var(--primary)]/10"
                      />
                    </label>
                  </div>
                </div>

                {statusText ? (
                  <div
                    className={classNames(
                      "mt-4 text-sm font-medium",
                      statusTone === "success" && "text-emerald-600",
                      statusTone === "error" && "text-rose-600",
                      statusTone === "default" && "text-[var(--chat-text-muted)]"
                    )}
                  >
                    {statusText}
                  </div>
                ) : null}
              </div>

              {config.mode === "edits" ? (
                <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-5 shadow-sm sm:p-6">
                  <div className="mb-4 flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-base font-semibold tracking-tight text-[var(--chat-text)]">
                        参考图像
                      </h3>
                      <p className="mt-1 text-sm leading-6 text-[var(--chat-text-muted)]">
                        支持多张图片、拖拽上传和局部涂抹编辑。未编辑时会把整张图片作为参考图。
                      </p>
                    </div>
                    <div className="rounded-full bg-[var(--chat-surface-muted)] px-3 py-1 text-[11px] font-medium text-[var(--chat-text-muted)]">
                      多图 · 蒙版
                    </div>
                  </div>

                  <label
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => {
                      event.preventDefault();
                      if (event.dataTransfer?.files?.length) {
                        void addFiles(event.dataTransfer.files);
                      }
                    }}
                    className="flex cursor-pointer flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/80 px-4 py-8 text-center transition hover:border-[var(--primary)]/30 hover:bg-[var(--primary)]/5"
                  >
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-[var(--chat-surface-soft)] text-[var(--primary)] shadow-sm ring-1 ring-[var(--chat-border)]">
                      <UploadCloud className="h-5 w-5" />
                    </div>
                    <div>
                      <div className="text-sm font-semibold text-[var(--chat-text)]">
                        点击、拖拽或 `Ctrl + V` 粘贴图片
                      </div>
                      <div className="mt-1 text-xs text-[var(--chat-text-muted)]">
                        支持 PNG / JPG / WEBP 等常见格式
                      </div>
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

                  {images.length ? (
                    <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
                      {images.map((item, index) => (
                        <div
                          key={item.id}
                          className="group relative overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-muted)]"
                        >
                          <img
                            src={item.objectUrl}
                            alt={`参考图 ${index + 1}`}
                            className="aspect-square w-full object-cover"
                          />
                          <div className="absolute left-2 top-2 rounded-full bg-[var(--chat-surface)]/90 px-2 py-0.5 text-[11px] font-semibold text-[var(--chat-text)] shadow-sm">
                            #{index + 1}
                          </div>
                          {item.maskDataUrl ? (
                            <div className="absolute right-2 top-2 rounded-full bg-rose-500 px-2 py-0.5 text-[11px] font-semibold text-white shadow-sm">
                              已涂抹
                            </div>
                          ) : null}
                          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/45 opacity-0 transition group-hover:opacity-100">
                            <button
                              type="button"
                              onClick={() => openEditor(item.id)}
                              className="inline-flex items-center gap-1 rounded-full bg-[var(--primary)] px-3 py-1.5 text-[12px] font-semibold text-white shadow"
                            >
                              <ImagePlus className="h-3.5 w-3.5" />
                              <span>{item.maskDataUrl ? "修改涂抹" : "编辑涂抹"}</span>
                            </button>
                            <button
                              type="button"
                              onClick={() => removeImage(item.id)}
                              className="inline-flex items-center gap-1 rounded-full bg-rose-600 px-3 py-1.5 text-[12px] font-semibold text-white shadow"
                            >
                              <X className="h-3.5 w-3.5" />
                              <span>移除</span>
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : null}

                  {editingImage ? (
                    <div className="mt-4 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4">
                      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                        <div className="text-sm font-semibold text-[var(--chat-text)]">
                          编辑第 {images.findIndex((item) => item.id === editingImage.id) + 1} 张（可选）
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                          <button
                            type="button"
                            onClick={clearCurrentMask}
                            className="inline-flex items-center gap-1 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-3 py-1.5 text-[12px] font-semibold text-[var(--chat-text-soft)] transition hover:border-rose-200 hover:text-rose-600"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                            <span>清除涂抹</span>
                          </button>
                          <button
                            type="button"
                            onClick={closeEditor}
                            className="inline-flex items-center gap-1 rounded-full bg-[var(--primary)] px-3 py-1.5 text-[12px] font-semibold text-white transition hover:bg-[var(--primary)]/90"
                          >
                            <Sparkles className="h-3.5 w-3.5" />
                            <span>完成</span>
                          </button>
                        </div>
                      </div>

                      <div className="overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-3">
                        <div className="relative mx-auto w-fit">
                          <img
                            ref={editorImageRef}
                            src={editingImage.objectUrl}
                            alt="编辑中的参考图"
                            draggable={false}
                            onLoad={() => {
                              // 图片重新布局后触发 useEffect 中的同步逻辑。
                              setImages((previous) => [...previous]);
                            }}
                            className="block max-h-[360px] max-w-full select-none rounded-lg"
                          />
                          <canvas
                            ref={maskCanvasRef}
                            className="absolute inset-0 cursor-crosshair rounded-lg touch-none"
                          />
                        </div>
                      </div>

                      <div className="mt-3 flex flex-wrap items-center gap-2">
                        <div className="inline-flex rounded-full bg-[var(--chat-surface-muted)] p-1">
                          <button
                            type="button"
                            onClick={() => setToolMode("brush")}
                            className={classNames(
                              "inline-flex items-center gap-1 rounded-full px-3 py-1.5 text-[12px] font-semibold transition",
                              toolMode === "brush"
                                ? "bg-[var(--chat-surface-soft)] text-[var(--chat-text)] shadow-sm"
                                : "text-[var(--chat-text-muted)]"
                            )}
                          >
                            <Brush className="h-3.5 w-3.5" />
                            <span>笔刷</span>
                          </button>
                          <button
                            type="button"
                            onClick={() => setToolMode("eraser")}
                            className={classNames(
                              "inline-flex items-center gap-1 rounded-full px-3 py-1.5 text-[12px] font-semibold transition",
                              toolMode === "eraser"
                                ? "bg-[var(--chat-surface-soft)] text-[var(--chat-text)] shadow-sm"
                                : "text-[var(--chat-text-muted)]"
                            )}
                          >
                            <Eraser className="h-3.5 w-3.5" />
                            <span>擦除</span>
                          </button>
                        </div>

                        <div className="inline-flex items-center gap-3 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-4 py-2 text-[12px] font-medium text-[var(--chat-text-soft)]">
                          <span>笔刷大小</span>
                          <input
                            type="range"
                            min={8}
                            max={96}
                            step={2}
                            value={brushSize}
                            onChange={(event) => setBrushSize(Number(event.target.value))}
                          />
                          <span className="font-mono text-[var(--chat-text)]">{brushSize}</span>
                        </div>
                      </div>
                    </div>
                  ) : null}
                </div>
              ) : null}

              <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] p-5 shadow-sm sm:p-6">
                <div className="mb-4 flex items-start justify-between gap-4">
                  <div>
                    <h3 className="text-base font-semibold tracking-tight text-[var(--chat-text)]">
                      历史图片
                    </h3>
                    <p className="mt-1 text-sm leading-6 text-[var(--chat-text-muted)]">
                      展示当前设备最近的生图批次，仅记录文生图与图生图成功结果。
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => void loadHistory(1, true)}
                    className="inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-1.5 text-[12px] font-semibold text-[var(--chat-text-soft)] transition hover:border-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
                  >
                    <RefreshCcw className={classNames("h-3.5 w-3.5", historyLoading && "animate-spin")} />
                    <span>刷新</span>
                  </button>
                </div>

                {historyLoading && !historyBatches.length ? (
                  <div className="rounded-xl border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/70 px-4 py-8 text-center text-sm text-[var(--chat-text-muted)]">
                    正在加载历史图片...
                  </div>
                ) : null}

                {!historyLoading && !historyBatches.length && !historyError ? (
                  <div className="rounded-xl border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/70 px-4 py-8 text-center text-sm text-[var(--chat-text-muted)]">
                    当前设备还没有生成历史
                  </div>
                ) : null}

                {historyError ? (
                  <div className="rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-600">
                    {historyError}
                  </div>
                ) : null}

                {historyBatches.length ? (
                  <div className="space-y-4">
                    {historyBatches.map((batch) => (
                      <div
                        key={batch.requestId}
                        className="rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4"
                      >
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--chat-text-muted)]">
                              {formatHistoryTime(batch.createdAt)}
                            </div>
                            <div className="mt-1 text-sm font-semibold leading-6 text-[var(--chat-text)]">
                              {batch.prompt}
                            </div>
                          </div>
                          <div className="flex flex-wrap gap-2 text-[11px] font-medium text-[var(--chat-text-muted)]">
                            <span className="rounded-full bg-[var(--chat-surface-muted)] px-2.5 py-1">
                              {batch.mode === "edits" ? "图生图" : "文生图"}
                            </span>
                            {batch.size ? (
                              <span className="rounded-full bg-[var(--chat-surface-muted)] px-2.5 py-1">
                                {batch.size}
                              </span>
                            ) : null}
                            {typeof batch.batchCount === "number" ? (
                              <span className="rounded-full bg-[var(--chat-surface-muted)] px-2.5 py-1">
                                {batch.batchCount} 张
                              </span>
                            ) : null}
                          </div>
                        </div>

                        {batch.images.length ? (
                          <div className="mt-3 grid grid-cols-2 gap-3">
                            {batch.images.map((item, index) => {
                              const previewUrl = resolvePreviewUrl(item);
                              const downloadUrl = resolveDownloadUrl(item);
                              return (
                                <div
                                  key={`${batch.requestId}-${item.fileName || index}`}
                                  className="overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-muted)]"
                                >
                                  <div className="p-2" style={checkerboardStyle}>
                                    <img
                                      src={previewUrl}
                                      alt={item.fileName || `历史结果图 ${index + 1}`}
                                      className="mx-auto h-28 w-full rounded-lg object-contain"
                                    />
                                  </div>
                                  <div className="flex items-center justify-between gap-3 px-3 py-2 text-[12px] text-[var(--chat-text-muted)]">
                                    <span className="truncate text-[var(--chat-text)]">
                                      {item.fileName || `结果图 ${index + 1}`}
                                    </span>
                                    {downloadUrl ? (
                                      <a
                                        href={downloadUrl}
                                        target="_blank"
                                        rel="noreferrer"
                                        className="inline-flex items-center gap-1 font-semibold text-[var(--primary)] transition hover:text-[var(--primary)]/80"
                                      >
                                        <Download className="h-3.5 w-3.5" />
                                        <span>打开</span>
                                      </a>
                                    ) : null}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        ) : null}
                      </div>
                    ))}

                    {historyBatches.length < historyTotal ? (
                      <button
                        type="button"
                        onClick={() => void loadHistory(historyPageNo + 1, false)}
                        disabled={historyLoadingMore}
                        className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-3 text-sm font-semibold text-[var(--chat-text-soft)] transition hover:border-[var(--chat-text-muted)] hover:text-[var(--chat-text)] disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <RefreshCcw className={classNames("h-4 w-4", historyLoadingMore && "animate-spin")} />
                        <span>{historyLoadingMore ? "加载中..." : "加载更多历史"}</span>
                      </button>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>

            <div className="min-w-0">
              <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] shadow-sm">
                <div className="border-b border-[var(--chat-border)] px-5 py-4 sm:px-6">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="text-[11px] font-semibold uppercase tracking-[0.24em] text-[var(--chat-text-muted)]">
                        Result Stream
                      </div>
                      <h3 className="mt-1 text-lg font-semibold tracking-tight text-[var(--chat-text)]">
                        生成记录
                      </h3>
                    </div>
                    <button
                      type="button"
                      onClick={clearMessages}
                      className="inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-2 text-sm font-semibold text-[var(--chat-text-soft)] transition hover:border-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
                    >
                      <Trash2 className="h-4 w-4" />
                      <span>清空记录</span>
                    </button>
                  </div>
                </div>

                <div ref={chatRef} className="max-h-[620px] min-h-[360px] overflow-y-auto px-5 py-5 sm:px-6">
                  {messages.length ? (
                    <div className="space-y-6">
                      {messages.map((message) =>
                        message.role === "user" ? (
                          <div key={message.id} className="flex flex-col items-end gap-2">
                            <div className="px-1 text-[11px] font-semibold uppercase tracking-[0.2em] text-[var(--primary)]">
                              User
                            </div>
                            <div className="max-w-[92%] rounded-2xl rounded-br-md bg-[var(--primary)] px-4 py-3 text-sm leading-6 text-white shadow-sm">
                              {message.mode === "edits" && message.images.length ? (
                                <div className="mb-3 flex flex-wrap gap-2">
                                  {message.images.map((imageUrl, index) => (
                                    <img
                                      key={`${message.id}-${index}`}
                                      src={imageUrl}
                                      alt={`参考图 ${index + 1}`}
                                      className="h-14 w-14 rounded-lg object-cover ring-1 ring-white/25"
                                    />
                                  ))}
                                </div>
                              ) : null}
                              <div>{message.prompt}</div>
                            </div>
                          </div>
                        ) : (
                          <div key={message.id} className="flex flex-col items-start gap-2">
                            <div className="px-1 text-[11px] font-semibold uppercase tracking-[0.2em] text-[var(--chat-text-muted)]">
                              Assistant
                            </div>
                            <div className="max-w-[96%] rounded-2xl rounded-bl-md border border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-4 text-sm text-[var(--chat-text)] shadow-sm">
                              {message.status === "loading" ? (
                                <div className="inline-flex items-center gap-3 text-[var(--chat-text-muted)]">
                                  <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-[var(--primary)]/10 text-[var(--primary)]">
                                    <Sparkles className="h-4 w-4 animate-pulse" />
                                  </span>
                                  <span className="font-medium">正在生成图像...</span>
                                </div>
                              ) : null}

                              {message.status !== "loading" ? (
                                <>
                                  {message.images.length ? (
                                    <div className="grid gap-3 sm:grid-cols-2">
                                      {message.images.map((item) => (
                                        <div key={item.url} className="overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface-muted)]">
                                          <div className="p-2" style={checkerboardStyle}>
                                            <img
                                              src={item.url}
                                              alt={item.label}
                                              className="mx-auto max-h-[280px] w-full rounded-lg object-contain"
                                            />
                                          </div>
                                          <div className="flex items-center justify-between gap-3 px-3 py-3 text-[12px] text-[var(--chat-text-muted)]">
                                            <span className="truncate font-medium text-[var(--chat-text)]">{item.label}</span>
                                            {item.downloadUrl ? (
                                              <a
                                                href={item.downloadUrl}
                                                target="_blank"
                                                rel="noreferrer"
                                                className="inline-flex items-center gap-1 font-semibold text-[var(--primary)] transition hover:text-[var(--primary)]/80"
                                              >
                                                <Download className="h-3.5 w-3.5" />
                                                <span>打开</span>
                                              </a>
                                            ) : null}
                                          </div>
                                        </div>
                                      ))}
                                    </div>
                                  ) : null}

                                  {message.summary ? (
                                    <div className={classNames(message.images.length && "mt-3", "leading-6 text-[var(--chat-text)]")}>
                                      {message.summary}
                                    </div>
                                  ) : null}

                                  {message.text ? (
                                    <pre className="mt-3 overflow-auto rounded-xl bg-[var(--chat-surface-muted)] px-4 py-3 whitespace-pre-wrap text-[13px] leading-6 text-[var(--chat-text-soft)]">
                                      {message.text}
                                    </pre>
                                  ) : null}

                                  {message.error ? (
                                    <div className="mt-3 rounded-xl border border-rose-100 bg-rose-50 px-4 py-3 text-[13px] leading-6 text-rose-600">
                                      {message.error}
                                    </div>
                                  ) : null}
                                </>
                              ) : null}
                            </div>
                          </div>
                        )
                      )}
                    </div>
                  ) : (
                    <div className="flex h-full min-h-[320px] items-center justify-center rounded-xl border border-dashed border-[var(--chat-border)] bg-[var(--chat-surface)]/70 px-8 py-12 text-center text-sm font-medium text-[var(--chat-text-muted)]">
                      在下方输入 Prompt 发起生图请求
                    </div>
                  )}
                </div>

                <div className="border-t border-[var(--chat-border)] px-5 py-4 sm:px-6">
                  <div className="rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-3">
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
                          ? "描述如何使用或修改这些图片，例如：把第一张图里的天空替换成晚霞，并保留建筑细节"
                          : "描述你要生成的画面内容..."
                      }
                      className="min-h-[120px] w-full resize-none rounded-xl border-none bg-transparent px-3 py-3 text-[15px] leading-7 text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
                    />
                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                      <div className="rounded-full bg-[var(--chat-surface-muted)] px-3 py-1.5 text-[12px] text-[var(--chat-text-muted)]">
                        当前模式：{config.mode === "images" ? "文生图" : config.mode === "edits" ? "图生图" : "对话调试"}
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setPrompt("")}
                          className="inline-flex items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] px-4 py-2 text-sm font-semibold text-[var(--chat-text-soft)] transition hover:border-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
                        >
                          <Trash2 className="h-4 w-4" />
                          <span>清空输入</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleSend()}
                          className="inline-flex items-center gap-2 rounded-full bg-[var(--primary)] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[var(--primary)]/90"
                        >
                          <SendHorizontal className="h-4 w-4" />
                          <span>发送</span>
                        </button>
                      </div>
                    </div>
                  </div>

                  <details className="mt-4 overflow-hidden rounded-xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/80">
                    <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-[var(--chat-text)]">
                      <span className="inline-flex items-center gap-2">
                        <Code2 className="h-4 w-4" />
                        <span>原始响应调试面板</span>
                      </span>
                      <span className="text-xs font-medium text-[var(--chat-text-muted)]">展开查看</span>
                    </summary>
                    <pre className="max-h-[320px] overflow-auto border-t border-[var(--chat-border)] px-4 py-4 whitespace-pre-wrap font-mono text-[12px] leading-6 text-[var(--chat-text-soft)]">
                      {toPrettyJson(debugPayload)}
                    </pre>
                  </details>
                </div>
              </div>
            </div>
          </section>
        )}
    </div>
  );
};

export default WorkspaceImageGeneration;
