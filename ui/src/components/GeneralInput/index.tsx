import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowUpIcon,
  BarChart3Icon,
  BrainCircuitIcon,
  CheckIcon,
  ChevronDownIcon,
  ListTodo,
  SearchIcon,
  Type,
} from "lucide-react";

import { AI_CHAT_FLOATING_CLASS } from "@/components/ai-elements/ai-chat-surface";
import {
  PromptInput,
  PromptInputAddAttachmentsButton,
  type PromptInputAttachmentError,
  type PromptInputAttachmentItem,
  PromptInputAttachments,
  PromptInputBody,
  PromptInputFooter,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
} from "@/components/ai-elements/prompt-input";
import { DropdownMenu, DropdownMenuContent, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { showMessage } from "@/utils";
import {
  resolveAdjacentPromptPlaceholder,
  type PromptSelectionRequest,
} from "@/utils/suggestedPrompts";
import {
  GENERIC_TASK_PRODUCT,
  defaultProduct,
  productList,
} from "@/utils/constants";
import UploadAttachmentChip from "./UploadAttachmentChip";
import {
  catalogApi,
  type CatalogModelRecord,
} from "@/services/catalog";
import ContextRing, { type ContextUsageView } from "./ContextRing";
import MarkdownBar from "./MarkdownBar";
import ModelPicker from "./ModelPicker";
import ThinkingToggle, { type ThinkingEffort } from "./ThinkingToggle";
import BrowserRelayChip from "./BrowserRelayChip";
import AgentBrowserChip from "./AgentBrowserChip";
import BackgroundAgentsChip from "./BackgroundAgentsChip";
import { buildSubmitPayload } from "./inputMode";
import { useAttachmentUploads } from "./useAttachmentUploads";
import PromptContextChips from "./PromptContextChips";
import { useComposerPromptContexts } from "@/hooks/useComposerPromptContexts";
import { serializeComposerPromptContexts } from "@/lib/promptContexts";

type Props = {
  sessionId: string;
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
  initialFiles?: File[];
  onInitialFilesConsumed?: () => void;
  /** SSE 推送的上下文占用 */
  contextUsage?: ContextUsageView | null;
  /** Agent 任务进行中时，在发送按钮旁展示轻量运行指示 */
  busy?: boolean;
  /** 任务进行中点击发送区停止本轮 */
  onStop?: () => void;
  /**
   * 任务进行中注入指导（控制面，不开新 run）。
   * 提供后 busy 时仍可输入并发送，走 inject 而非 send。
   */
  onInject?: (text: string) => void | Promise<void>;
  size: string;
  product?: CHAT.Product;
  deepThink?: boolean;
  send: (p: CHAT.TInputInfo) => void;
  onSelectionChange?: (selection: { product: CHAT.Product; deepThink: boolean }) => void;
  /** 外部回填（Undo 上一轮 user query） */
  draftMessage?: string | null;
  onDraftConsumed?: () => void;
  /** 当前会话未发送的输入草稿 */
  draftValue?: string;
  onDraftChange?: (value: string) => void;
  /** 推荐模板回填后的选区请求，用于直接覆盖占位词 */
  promptSelectionRequest?: PromptSelectionRequest | null;
  /** 后台子智能体数量（输入框徽标，与右上角面板联动） */
  backgroundAgentCount?: number;
  /** 其中仍在运行的数量（徽标 tooltip 用） */
  backgroundAgentRunningCount?: number;
  /** 点击徽标 → 展开右上角智能体面板 */
  onOpenBackgroundAgents?: () => void;
};

type InputModeKey = "think" | "research";

const DATA_AGENT_PRODUCT =
  (productList.find((item) => item.type === "dataAgent") as CHAT.Product | undefined) ?? defaultProduct;

const MODE_OPTIONS: Array<{
  key: InputModeKey;
  label: string;
  description: string;
  icon: typeof BrainCircuitIcon;
}> = [
  {
    key: "think",
    label: "ReAct",
    description: "边思考边执行工具",
    icon: BrainCircuitIcon,
  },
  {
    key: "research",
    label: "PlanExecute",
    description: "先规划，再执行任务",
    icon: SearchIcon,
  },
];

const VISIBLE_MODE_OPTIONS = MODE_OPTIONS;

/** 对话附件不按扩展名拦截；上传/登记链路对任意文件落盘。空字符串表示原生 file picker 与拖拽都接受全部类型。 */
export const ATTACHMENT_ACCEPT = "";

/** 单条 query 最大字符数（前端硬限制） */
export const MAX_QUERY_CHARS = 8000;

const getModeKey = (_productType?: string, deepThink = false): InputModeKey => {
  return deepThink ? "research" : "think";
};

/* ZCode 菜单规范：shell rounded-lg + border-popover-border + shadow-md，
   item 用 rounded-md、hover 用 bg-menu-hover。 */
const menuContentClassName =
  "rounded-lg border border-popover-border bg-popover p-1 shadow-md";

const menuTitleClassName =
  "px-2.5 pb-1 pt-1.5 text-ui-xs font-medium text-foreground-subtlest";

const menuItemClassName = (active: boolean) =>
  cn(
    "flex w-full items-center gap-2 rounded-md px-2 py-2 text-left transition-colors",
    active ? "bg-menu-hover" : "hover:bg-menu-hover"
  );

const toolBtnClassName = (active?: boolean, disabled?: boolean) =>
  cn(
    "reactor-composer-tool inline-flex h-8 max-w-full items-center gap-1 rounded-md px-2 text-ui-base font-medium transition-colors",
    "text-foreground-subtle hover:bg-hover hover:text-foreground",
    active && "text-foreground",
    disabled && "cursor-not-allowed opacity-45 hover:bg-transparent hover:text-foreground-subtle"
  );

const GeneralInput: ReactorType.FC<Props> = (props) => {
  const {
    sessionId,
    placeholder,
    showBtn,
    disabled,
    initialFiles,
    onInitialFilesConsumed,
    contextUsage = null,
    busy = false,
    onStop,
    onInject,
    size,
    product,
    deepThink = false,
    send,
    onSelectionChange,
    draftMessage = null,
    onDraftConsumed,
    draftValue = "",
    onDraftChange,
    promptSelectionRequest = null,
    backgroundAgentCount = 0,
    backgroundAgentRunningCount = 0,
    onOpenBackgroundAgents,
  } = props;

  const [question, setQuestion] = useState(() =>
    draftValue.slice(0, MAX_QUERY_CHARS)
  );
  const [modeMenuOpen, setModeMenuOpen] = useState(false);
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const [thinkingMenuOpen, setThinkingMenuOpen] = useState(false);
  const [markdownBarOpen, setMarkdownBarOpen] = useState(false);
  const [models, setModels] = useState<CatalogModelRecord[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>("");
  const [thinking, setThinking] = useState(true);
  const [thinkingEffort, setThinkingEffort] = useState<ThinkingEffort>("high");
  const [forcePlanMode, setForcePlanMode] = useState(false);
  const tempData = useRef<{ compositing?: boolean }>({});
  const inputShellRef = useRef<HTMLDivElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const appliedPromptRequestRef = useRef<number | null>(null);
  const {
    attachmentUploads,
    attachmentOrder,
    clearAttachmentUploads,
    removeAttachmentUpload,
    retryAttachmentUpload,
    addAttachmentUploads,
  } = useAttachmentUploads(sessionId);
  const promptContexts = useComposerPromptContexts(sessionId);

  const currentMode = getModeKey(product?.type, deepThink);
  const isDataAgent = product?.type === "dataAgent";

  const modelStorageKey = `reactor:selected-model:${sessionId}`;

  useEffect(() => {
    // 目录失败不阻断对话；空列表表示使用后端默认模型。
    void catalogApi
      .listModels()
      .then((list) => {
        const enabledByModelId = new Map<string, CatalogModelRecord>();
        if (Array.isArray(list)) {
          for (const model of list) {
            if (
              (model.status ?? 1) !== 1 ||
              enabledByModelId.has(model.modelId)
            ) {
              continue;
            }
            enabledByModelId.set(model.modelId, model);
          }
        }
        const enabled = Array.from(enabledByModelId.values());
        setModels(enabled);
        setSelectedModel(() => {
          const stored = window.sessionStorage.getItem(modelStorageKey) || "";
          const candidate = stored;
          if (candidate && enabled.some((m) => m.modelId === candidate || m.modelName === candidate)) {
            return candidate;
          }
          const fallback = enabled[0]?.modelId || "";
          if (fallback) {
            window.sessionStorage.setItem(modelStorageKey, fallback);
          }
          return fallback;
        });
      })
      .catch(() => {
        setModels([]);
      });
  }, [modelStorageKey]);

  const handleModelChange = (modelId: string) => {
    setSelectedModel(modelId);
    window.sessionStorage.setItem(modelStorageKey, modelId);
  };

  const visibleMode = currentMode;
  const currentModeOption =
    MODE_OPTIONS.find((item) => item.key === visibleMode) ?? MODE_OPTIONS[0];
  const CurrentModeIcon = currentModeOption.icon;

  const hasUploadingAttachment = attachmentOrder.some((id) => {
    const status = attachmentUploads[id]?.status;
    return status === "pending" || status === "uploading";
  });
  const hasFailedAttachment = attachmentOrder.some(
    (id) => attachmentUploads[id]?.status === "error"
  );
  const uploadedFiles = attachmentOrder
    .map((id) => attachmentUploads[id]?.uploadedFile)
    .filter((file): file is CHAT.TFile => Boolean(file));
  const canInject =
    Boolean(onInject) &&
    Boolean(question.trim()) &&
    !disabled &&
    busy &&
    !hasUploadingAttachment &&
    !hasFailedAttachment;
  const canSend =
    Boolean(question.trim()) &&
    !disabled &&
    !busy &&
    !hasUploadingAttachment &&
    !hasFailedAttachment;
  const canSubmit = canSend || canInject;
  const showDataAgentToggle = showBtn;
  const showPlanToggle = !showBtn && !isDataAgent && visibleMode === "research";

  const currentModelMeta = models.find(
    (m) => m.modelId === selectedModel || m.modelName === selectedModel
  );
  const supportsThinking =
    (currentModelMeta?.supportsThinking ?? 0) === 1 ||
    /grok|o1|o3|reason|r1/i.test(currentModelMeta?.modelName || selectedModel);

  useEffect(() => {
    const root = inputShellRef.current;
    if (!root) return;
    const el = root.querySelector("textarea");
    textareaRef.current = el as HTMLTextAreaElement | null;
  });

  useEffect(() => {
    const nextDraft = draftValue.slice(0, MAX_QUERY_CHARS);
    setQuestion((previous) => (previous === nextDraft ? previous : nextDraft));
  }, [draftValue]);

  useEffect(() => {
    const request = promptSelectionRequest;
    if (
      !request ||
      appliedPromptRequestRef.current === request.requestId ||
      question !== request.text
    ) {
      return;
    }

    const textarea =
      textareaRef.current ||
      (inputShellRef.current?.querySelector("textarea") as HTMLTextAreaElement | null);
    if (!textarea) {
      return;
    }

    const firstPlaceholder = request.placeholderRanges[0];
    textarea.focus();
    textarea.setSelectionRange(
      firstPlaceholder?.start ?? question.length,
      firstPlaceholder?.end ?? question.length
    );
    appliedPromptRequestRef.current = request.requestId;
  }, [promptSelectionRequest, question]);

  useEffect(() => {
    if (!draftMessage) return;
    const nextDraft = draftMessage.slice(0, MAX_QUERY_CHARS);
    setQuestion(nextDraft);
    onDraftChange?.(nextDraft);
    onDraftConsumed?.();
    requestAnimationFrame(() => {
      textareaRef.current?.focus();
    });
  }, [draftMessage, onDraftChange, onDraftConsumed]);

  const handleAttachmentError = useCallback((error: PromptInputAttachmentError) => {
    if (error.code === "accept") {
      showMessage()?.warning("不支持该文件类型");
      return;
    }
    if (error.code === "max_file_size") {
      showMessage()?.warning("文件过大，请压缩后再试");
      return;
    }
    if (error.code === "max_files") {
      showMessage()?.warning("一次添加的文件过多，部分未加入");
    }
  }, []);

  const handleAttachmentsAdded = useCallback(
    (attachments: PromptInputAttachmentItem[]) => {
      const nextAttachments = attachments.filter(
        (attachment): attachment is PromptInputAttachmentItem & { file: File } =>
          Boolean(attachment.file)
      );
      addAttachmentUploads(
        nextAttachments.map((attachment) => ({
          id: attachment.id,
          file: attachment.file,
        }))
      );
    },
    [addAttachmentUploads]
  );

  const handleSelectionChange = (nextProduct: CHAT.Product, nextDeepThink: boolean) => {
    onSelectionChange?.({
      product: nextProduct,
      deepThink: nextDeepThink,
    });
  };

  const handleModeSelect = (modeKey: InputModeKey) => {
    // 普通模式统一走 ReAct，研究模式统一走 PlanSolve。
    handleSelectionChange(GENERIC_TASK_PRODUCT, modeKey === "research");
    if (modeKey !== "research") {
      setForcePlanMode(false);
    }
    setModeMenuOpen(false);
  };

  const handleQuestionChange = (value: string) => {
    const nextQuestion =
      value.length > MAX_QUERY_CHARS ? value.slice(0, MAX_QUERY_CHARS) : value;
    setQuestion(nextQuestion);
    onDraftChange?.(nextQuestion);
  };

  const handleSubmit = ({ text }: { text: string; files: unknown[] }) => {
    const trimmed = text.trim().slice(0, MAX_QUERY_CHARS);
    if (!trimmed || disabled || hasUploadingAttachment || hasFailedAttachment) return;

    // 预览区上下文（代码评论 / 网页元素 / 选段）拼成 prompt 尾块。
    // **尾块不参与正文的 8000 截断** —— 正文先截断，再拼接（与 ZCode 一致）。
    const messageWithContexts = serializeComposerPromptContexts(trimmed, promptContexts.contexts);

    if (busy && onInject) {
      void Promise.resolve(onInject(messageWithContexts));
      setQuestion("");
      onDraftChange?.("");
      setForcePlanMode(false);
      promptContexts.clear();
      return;
    }
    if (busy) {
      return;
    }

    send(
      buildSubmitPayload({
        question: messageWithContexts,
        visibleMode,
        isDataAgent,
        uploadedFiles,
        model: selectedModel || undefined,
        thinking: supportsThinking ? thinking : undefined,
        thinkingEffort: supportsThinking && thinking ? thinkingEffort : undefined,
        forcePlanMode: showPlanToggle && forcePlanMode,
      })
    );

    setQuestion("");
    onDraftChange?.("");
    setForcePlanMode(false);
    clearAttachmentUploads();
    promptContexts.clear();
  };

  const handleKeyDown: React.KeyboardEventHandler<HTMLTextAreaElement> = (event) => {
    if (event.key === "Tab" && promptSelectionRequest?.placeholders.length) {
      const textarea = event.currentTarget;
      const direction: 1 | -1 = event.shiftKey ? -1 : 1;
      const nextPlaceholder = resolveAdjacentPromptPlaceholder(
        question,
        promptSelectionRequest,
        textarea.selectionStart,
        textarea.selectionEnd,
        direction
      );
      if (nextPlaceholder) {
        event.preventDefault();
        textarea.focus();
        textarea.setSelectionRange(nextPlaceholder.start, nextPlaceholder.end);
        return;
      }
    }

    if (event.key !== "Enter") return;
    if (tempData.current.compositing || event.nativeEvent.isComposing) return;

    if (event.metaKey || event.ctrlKey) {
      event.preventDefault();
      if (question.length >= MAX_QUERY_CHARS) {
        return;
      }
      const textarea = event.currentTarget;
      const { selectionStart, selectionEnd } = textarea;
      const nextValue = (
        question.slice(0, selectionStart) + "\n" + question.slice(selectionEnd)
      ).slice(0, MAX_QUERY_CHARS);
      handleQuestionChange(nextValue);
      requestAnimationFrame(() => {
        const caret = Math.min(selectionStart + 1, nextValue.length);
        textarea.selectionStart = caret;
        textarea.selectionEnd = caret;
        textarea.focus();
      });
      return;
    }

    if (!canSubmit) {
      event.preventDefault();
      return;
    }

    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  };

  return (
    <TooltipProvider>
      <div className="w-full" ref={inputShellRef}>
        <PromptInput
          accept={ATTACHMENT_ACCEPT}
          className="reactor-input-flat w-full"
          convertBlobUrlsOnSubmit={false}
          initialFiles={initialFiles}
          onInitialFilesConsumed={onInitialFilesConsumed}
          multiple={true}
          onAttachmentsAdded={handleAttachmentsAdded}
          onError={handleAttachmentError}
          onSubmit={handleSubmit}
        >
          <PromptInputBody>
            <PromptInputAttachments className="reactor-composer-attachments px-1.5 pt-2.5 pb-0">
              {(file) => (
                <UploadAttachmentChip
                  key={file.id}
                  attachment={file}
                  uploadState={attachmentUploads[file.id]}
                  onRemoveAttachment={removeAttachmentUpload}
                  onRetryAttachment={retryAttachmentUpload}
                />
              )}
            </PromptInputAttachments>

            <PromptContextChips
              className="px-1.5 pt-2 pb-0"
              contexts={promptContexts.contexts}
              onRemoveCodeComment={promptContexts.removeCodeComment}
              onRemoveWebElement={promptContexts.removeWebElement}
              onRemoveConversationSelection={promptContexts.removeConversationSelection}
            />

            {markdownBarOpen ? (
              <MarkdownBar
                textareaRef={textareaRef}
                value={question}
                onChange={handleQuestionChange}
                disabled={disabled}
              />
            ) : null}

            <PromptInputTextarea
              className={cn(
                "reactor-composer-textarea px-4 text-ui-lg leading-[1.5] text-foreground placeholder:text-foreground-subtlest placeholder:opacity-100",
                size === "big"
                  ? "reactor-composer-textarea-big min-h-[76px] pt-3.5"
                  : "reactor-composer-textarea-medium min-h-[54px] pt-3"
              )}
              disabled={disabled}
              maxLength={MAX_QUERY_CHARS}
              onChange={(event) => handleQuestionChange(event.target.value)}
              onCompositionEnd={() => {
                tempData.current.compositing = false;
              }}
              onCompositionStart={() => {
                tempData.current.compositing = true;
              }}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              value={question}
            />
            {question.length >= Math.floor(MAX_QUERY_CHARS * 0.8) ? (
              <div
                className={cn(
                  "px-4 pb-1 text-right text-ui-xs tabular-nums",
                  question.length >= MAX_QUERY_CHARS
                    ? "text-destructive"
                    : "text-foreground-subtlest"
                )}
              >
                {question.length}/{MAX_QUERY_CHARS}
              </div>
            ) : null}
          </PromptInputBody>

          <PromptInputFooter className="reactor-composer-footer items-center justify-between gap-2 px-2.5 pb-2 pt-0.5">
            <PromptInputTools className="reactor-composer-tools reactor-composer-tools-left min-w-0 flex-1 flex-wrap items-center gap-0.5">
              <PromptInputAddAttachmentsButton
                size="icon-sm"
                variant="ghost"
                disabled={disabled}
                className="reactor-composer-icon-button h-8 w-8 rounded-md border-0 bg-transparent text-foreground-subtle shadow-none ring-0 hover:bg-hover hover:text-foreground focus-visible:ring-0"
              />

              <BrowserRelayChip disabled={disabled} />
              <AgentBrowserChip disabled={disabled} />
              <BackgroundAgentsChip
                count={backgroundAgentCount}
                runningCount={backgroundAgentRunningCount}
                disabled={disabled}
                onOpen={onOpenBackgroundAgents}
              />

              {showPlanToggle ? (
                <button
                  type="button"
                  aria-pressed={forcePlanMode}
                  disabled={disabled || busy}
                  className={cn(
                    toolBtnClassName(false, disabled || busy),
                    forcePlanMode &&
                      "bg-brand text-foreground-inverse hover:bg-brand/90 hover:text-foreground-inverse"
                  )}
                  title={
                    busy
                      ? "任务进行中，指导不会进入计划模式"
                      : "本轮先规划再执行，发送后复位"
                  }
                  onClick={() => setForcePlanMode((value) => !value)}
                >
                  <ListTodo className="size-3.5 shrink-0 opacity-80" />
                  <span className="truncate">计划</span>
                </button>
              ) : null}

              {showBtn ? (
                <>
                  <DropdownMenu open={modeMenuOpen} onOpenChange={setModeMenuOpen}>
                    <DropdownMenuTrigger asChild>
                      <button
                        type="button"
                        disabled={disabled}
                        className={toolBtnClassName(!isDataAgent, disabled)}
                      >
                        <CurrentModeIcon className="size-3.5 shrink-0 opacity-80" />
                        <span className="truncate">{currentModeOption.label}</span>
                        <ChevronDownIcon
                          className={cn(
                            "size-3.5 shrink-0 opacity-50 transition-transform",
                            modeMenuOpen && "rotate-180"
                          )}
                        />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent
                      align="start"
                      side="bottom"
                      sideOffset={10}
                      className={cn("w-[200px]", menuContentClassName)}
                    >
                      <div className={menuTitleClassName}>模式</div>
                      <div className="space-y-0.5">
                        {VISIBLE_MODE_OPTIONS.map((option) => {
                          const isActive = option.key === visibleMode && !isDataAgent;
                          return (
                            <button
                              key={option.key}
                              type="button"
                              className={menuItemClassName(isActive)}
                              onClick={() => handleModeSelect(option.key)}
                            >
                              <option.icon className="size-3.5 shrink-0 text-foreground-subtle" />
                              <span className="min-w-0 flex-1">
                                <span className="block text-ui-base font-medium text-foreground">
                                  {option.label}
                                </span>
                                <span className="mt-0.5 block text-ui-xs leading-4 text-foreground-subtle">
                                  {option.description}
                                </span>
                              </span>
                              {isActive ? (
                                <CheckIcon className="size-3.5 shrink-0 text-foreground" />
                              ) : null}
                            </button>
                          );
                        })}
                      </div>
                    </DropdownMenuContent>
                  </DropdownMenu>

                  {showDataAgentToggle ? (
                    <button
                      type="button"
                      aria-pressed={isDataAgent}
                      disabled={disabled}
                      className={toolBtnClassName(isDataAgent, disabled)}
                      onClick={() => {
                        if (isDataAgent) {
                          handleSelectionChange(GENERIC_TASK_PRODUCT, false);
                          return;
                        }
                        handleSelectionChange(DATA_AGENT_PRODUCT, false);
                      }}
                    >
                      <BarChart3Icon className="size-3.5 shrink-0 opacity-80" />
                      <span className="truncate">数据分析</span>
                    </button>
                  ) : null}

                  <button
                    type="button"
                    disabled={disabled}
                    className={toolBtnClassName(markdownBarOpen, disabled)}
                    title="格式工具条（Markdown）"
                    onClick={() => setMarkdownBarOpen((v) => !v)}
                  >
                    <Type className="size-3.5 shrink-0 opacity-80" />
                  </button>

                  <ThinkingToggle
                    supported={supportsThinking}
                    thinking={thinking}
                    effort={thinkingEffort}
                    disabled={disabled}
                    open={thinkingMenuOpen}
                    onOpenChange={setThinkingMenuOpen}
                    triggerClassName={toolBtnClassName}
                    onChange={(on, effort) => {
                      setThinking(on);
                      setThinkingEffort(effort);
                    }}
                  />

                  <ModelPicker
                    models={models}
                    value={selectedModel}
                    onChange={handleModelChange}
                    disabled={disabled}
                    open={modelMenuOpen}
                    onOpenChange={setModelMenuOpen}
                    triggerClassName={toolBtnClassName}
                  />
                </>
              ) : null}
            </PromptInputTools>

            <PromptInputTools className="reactor-composer-tools reactor-composer-tools-right ml-auto shrink-0 items-center gap-1.5 self-end">
              <ContextRing
                usage={contextUsage}
                contextWindow={currentModelMeta?.contextWindow}
              />
              {busy && onStop ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <button
                      type="button"
                      className="reactor-composer-stop group relative flex size-8 items-center justify-center rounded-full border border-destructive bg-surface p-0 text-destructive transition-[background-color,border-color,color,transform] duration-150 hover:bg-destructive hover:text-destructive-foreground active:scale-[0.92]"
                      onClick={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        onStop();
                      }}
                      aria-label="停止"
                    >
                      <span className="block size-2.5 rounded-[2px] bg-current" />
                    </button>
                  </TooltipTrigger>
                  <TooltipContent className={AI_CHAT_FLOATING_CLASS} side="top">
                    停止
                  </TooltipContent>
                </Tooltip>
              ) : null}
              {(!busy || onInject) ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <PromptInputSubmit
                      className={cn(
                        "reactor-send-btn relative flex size-8 items-center justify-center rounded-full border-0 p-0 shadow-none transition-opacity",
                        "bg-primary text-primary-foreground hover:bg-primary/80",
                        "disabled:cursor-not-allowed disabled:bg-primary disabled:text-primary-foreground disabled:opacity-45"
                      )}
                      disabled={!canSubmit}
                      variant="ghost"
                    >
                      <ArrowUpIcon className="size-[15px] stroke-[2.25]" />
                    </PromptInputSubmit>
                  </TooltipTrigger>
                  <TooltipContent className={AI_CHAT_FLOATING_CLASS} side="top">
                    {busy && onInject ? "发送指导（注入当前任务）" : "发送"}
                  </TooltipContent>
                </Tooltip>
              ) : busy ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span className="sr-only">任务进行中</span>
                  </TooltipTrigger>
                  <TooltipContent className={AI_CHAT_FLOATING_CLASS} side="top">
                    任务进行中
                  </TooltipContent>
                </Tooltip>
              ) : null}
            </PromptInputTools>
          </PromptInputFooter>
        </PromptInput>
      </div>
    </TooltipProvider>
  );
};

export default GeneralInput;
