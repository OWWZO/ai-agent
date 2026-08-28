import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowUpIcon,
  BarChart3Icon,
  BrainCircuitIcon,
  CheckIcon,
  ChevronDownIcon,
  PlusIcon,
  SearchIcon,
  Type,
  ZapIcon,
} from "lucide-react";

import { AI_CHAT_FLOATING_CLASS } from "@/components/ai-elements/ai-chat-surface";
import {
  PromptInput,
  PromptInputActionAddAttachments,
  PromptInputActionMenu,
  PromptInputActionMenuContent,
  PromptInputActionMenuTrigger,
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
import ChatRoleSelector from "@/components/ChatRoleSelector";
import { cn } from "@/lib/utils";
import {
  GENERIC_TASK_PRODUCT,
  defaultProduct,
  productList,
} from "@/utils/constants";
import UploadAttachmentChip from "./UploadAttachmentChip";
import {
  isFallbackModelUsage,
  llmModelAdminApi,
  type LlmModelRecord,
} from "@/services/llmModelAdmin";
import ContextRing, { type ContextUsageView } from "./ContextRing";
import MarkdownBar from "./MarkdownBar";
import ModelPicker from "./ModelPicker";
import ThinkingToggle, { type ThinkingEffort } from "./ThinkingToggle";
import { buildSubmitPayload } from "./inputMode";
import { useAttachmentUploads } from "./useAttachmentUploads";

type Props = {
  sessionId: string;
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
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
  /** @deprecated 输出格式已下线，保留 prop 兼容旧调用 */
  displayOutput?: CHAT.Product;
  chatRole?: CHAT.ConversationRole | null;
  chatRoles?: CHAT.FixRole[];
  showRoleSelector?: boolean;
  send: (p: CHAT.TInputInfo) => void;
  onSelectionChange?: (selection: { product: CHAT.Product; deepThink: boolean }) => void;
  onRoleSelect?: (role: CHAT.FixRole) => void;
  /** 外部回填（Undo 上一轮 user query） */
  draftMessage?: string | null;
  onDraftConsumed?: () => void;
};

type InputModeKey = "quick" | "think" | "research";

const DATA_AGENT_PRODUCT =
  (productList.find((item) => item.type === "dataAgent") as CHAT.Product | undefined) ?? defaultProduct;

const MODE_OPTIONS: Array<{
  key: InputModeKey;
  label: string;
  description: string;
  icon: typeof ZapIcon;
}> = [
  {
    key: "quick",
    label: "快速",
    description: "即时问答",
    icon: ZapIcon,
  },
  {
    key: "think",
    label: "深度思考",
    description: "多步分析",
    icon: BrainCircuitIcon,
  },
  {
    key: "research",
    label: "深度研究",
    description: "长链路研究",
    icon: SearchIcon,
  },
];

const VISIBLE_MODE_OPTIONS = MODE_OPTIONS.filter((item) => item.key !== "quick");

/** 输入框附件 accept：图片 + 常见文档/代码/表格 */
export const ATTACHMENT_ACCEPT =
  "image/*,application/pdf,.txt,.md,.csv,.xlsx,.docx,.pptx,.json,.py,.html";

/** 单条 query 最大字符数（前端硬限制） */
export const MAX_QUERY_CHARS = 8000;

const getModeKey = (productType?: string, deepThink = false): InputModeKey => {
  if (productType === "chat") {
    return "quick";
  }
  return deepThink ? "research" : "think";
};

const menuContentClassName =
  "rounded-[18px] border border-black/[0.04] bg-white p-1 shadow-[0_12px_40px_-16px_rgba(15,23,42,0.28)]";

const menuTitleClassName =
  "px-2.5 pb-1 pt-1.5 text-[11px] font-medium text-[#86868b]";

const menuItemClassName = (active: boolean) =>
  cn(
    "flex w-full items-center gap-2 rounded-[12px] px-2 py-2 text-left transition-colors",
    active ? "bg-[#f5f5f7]" : "hover:bg-[#f5f5f7]/80"
  );

const toolBtnClassName = (active?: boolean, disabled?: boolean) =>
  cn(
    "reactor-composer-tool inline-flex h-8 max-w-full items-center gap-1 rounded-md px-2 text-[13px] font-medium tracking-[-0.01em] transition-colors",
    "text-[#6b6b70] hover:bg-black/[0.04] hover:text-[#1d1d1f]",
    active && "text-[#1d1d1f]",
    disabled && "cursor-not-allowed opacity-45 hover:bg-transparent hover:text-[#6b6b70]"
  );

const GeneralInput: ReactorType.FC<Props> = (props) => {
  const {
    sessionId,
    placeholder,
    showBtn,
    disabled,
    contextUsage = null,
    busy = false,
    onStop,
    onInject,
    size,
    product,
    deepThink = false,
    chatRole,
    chatRoles = [],
    showRoleSelector = false,
    send,
    onSelectionChange,
    onRoleSelect,
    draftMessage = null,
    onDraftConsumed,
  } = props;

  const [question, setQuestion] = useState("");
  const [modeMenuOpen, setModeMenuOpen] = useState(false);
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const [thinkingMenuOpen, setThinkingMenuOpen] = useState(false);
  const [markdownBarOpen, setMarkdownBarOpen] = useState(false);
  const [models, setModels] = useState<LlmModelRecord[]>([]);
  const [selectedModel, setSelectedModel] = useState<string>("");
  const [thinking, setThinking] = useState(false);
  const [thinkingEffort, setThinkingEffort] = useState<ThinkingEffort>(null);
  const tempData = useRef<{ compositing?: boolean }>({});
  const inputShellRef = useRef<HTMLDivElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const {
    attachmentUploads,
    attachmentOrder,
    clearAttachmentUploads,
    removeAttachmentUpload,
    retryAttachmentUpload,
    addAttachmentUploads,
  } = useAttachmentUploads(sessionId);

  const currentMode = getModeKey(product?.type, deepThink);
  const isDataAgent = product?.type === "dataAgent";

  // 记住离开数据分析前的推理模式
  const lastStandardModeRef = useRef<InputModeKey>(
    currentMode === "quick" ? "think" : currentMode
  );

  useEffect(() => {
    if (product?.type === "dataAgent") {
      return;
    }
    lastStandardModeRef.current = currentMode === "quick" ? "think" : currentMode;
  }, [currentMode, product?.type]);

  useEffect(() => {
    // 启用模型列表：供输入框热切换；失败不阻断对话（空列表=用后端默认）
    void llmModelAdminApi
      .listEnabledModels()
      .then((list) => {
        const enabledByModelId = new Map<string, LlmModelRecord>();
        if (Array.isArray(list)) {
          for (const model of list) {
            if (
              (model.status ?? 1) !== 1 ||
              isFallbackModelUsage(model.modelUsage) ||
              enabledByModelId.has(model.modelId)
            ) {
              continue;
            }
            enabledByModelId.set(model.modelId, model);
          }
        }
        const enabled = Array.from(enabledByModelId.values());
        setModels(enabled);
        setSelectedModel((prev) => {
          if (prev && enabled.some((m) => m.modelId === prev || m.modelName === prev)) {
            return prev;
          }
          return enabled[0]?.modelId || "";
        });
      })
      .catch(() => {
        setModels([]);
      });
  }, []);

  const visibleMode = isDataAgent ? lastStandardModeRef.current : currentMode;
  const currentModeOption =
    MODE_OPTIONS.find((item) => item.key === visibleMode) ?? MODE_OPTIONS[1];
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
  const showDataAgentToggle = showBtn && (isDataAgent || visibleMode !== "quick");

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
    if (!draftMessage) return;
    setQuestion(draftMessage.slice(0, MAX_QUERY_CHARS));
    onDraftConsumed?.();
    requestAnimationFrame(() => {
      textareaRef.current?.focus();
    });
  }, [draftMessage, onDraftConsumed]);

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
    if (modeKey === "quick") {
      setModeMenuOpen(false);
      return;
    }
    // 输出格式已下线：标准任务统一走通用 task
    handleSelectionChange(GENERIC_TASK_PRODUCT, modeKey === "research");
    setModeMenuOpen(false);
  };

  const handleQuestionChange = (value: string) => {
    setQuestion(value.length > MAX_QUERY_CHARS ? value.slice(0, MAX_QUERY_CHARS) : value);
  };

  const handleSubmit = ({ text }: { text: string; files: unknown[] }) => {
    const trimmed = text.trim().slice(0, MAX_QUERY_CHARS);
    if (!trimmed || disabled || hasUploadingAttachment || hasFailedAttachment) return;

    if (busy && onInject) {
      void Promise.resolve(onInject(trimmed));
      setQuestion("");
      return;
    }
    if (busy) {
      return;
    }

    send(
      buildSubmitPayload({
        question: trimmed,
        visibleMode,
        isDataAgent,
        currentProductType: product?.type,
        uploadedFiles,
        chatRole: chatRole || null,
        model: selectedModel || undefined,
        thinking: supportsThinking ? thinking : undefined,
        thinkingEffort: supportsThinking && thinking ? thinkingEffort : undefined,
      })
    );

    setQuestion("");
    clearAttachmentUploads();
  };

  const handleKeyDown: React.KeyboardEventHandler<HTMLTextAreaElement> = (event) => {
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
      setQuestion(nextValue);
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
          multiple
          onAttachmentsAdded={handleAttachmentsAdded}
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
                "reactor-composer-textarea px-4 text-[15px] leading-[1.5] text-[#1d1d1f] placeholder:text-[#9aa3af] placeholder:opacity-100",
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
                  "px-4 pb-1 text-right text-[11px] tabular-nums",
                  question.length >= MAX_QUERY_CHARS
                    ? "text-[#ff3b30]"
                    : "text-[#aeaeb2]"
                )}
              >
                {question.length}/{MAX_QUERY_CHARS}
              </div>
            ) : null}
          </PromptInputBody>

          <PromptInputFooter className="reactor-composer-footer items-center justify-between gap-2 px-2.5 pb-2 pt-0.5">
            <PromptInputTools className="reactor-composer-tools reactor-composer-tools-left min-w-0 flex-1 flex-wrap items-center gap-0.5">
              <PromptInputActionMenu>
                <PromptInputActionMenuTrigger
                  size="icon-sm"
                  variant="ghost"
                  disabled={disabled}
                  className="reactor-composer-icon-button h-8 w-8 rounded-md border-0 bg-transparent text-[#6b6b70] shadow-none ring-0 hover:bg-black/[0.04] hover:text-[#1d1d1f] focus-visible:ring-0"
                >
                  <PlusIcon className="size-4" />
                </PromptInputActionMenuTrigger>
                <PromptInputActionMenuContent className={cn("min-w-[168px]", menuContentClassName)}>
                  <PromptInputActionAddAttachments label="上传附件" />
                </PromptInputActionMenuContent>
              </PromptInputActionMenu>

              {showBtn ? (
                <>
                  {showRoleSelector ? (
                    <ChatRoleSelector
                      roles={chatRoles}
                      selectedRole={chatRole}
                      disabled={disabled}
                      onSelect={(role) => onRoleSelect?.(role)}
                    />
                  ) : null}

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
                              <option.icon className="size-3.5 shrink-0 text-[#6b6b70]" />
                              <span className="min-w-0 flex-1">
                                <span className="block text-[13.5px] font-medium text-[#1d1d1f]">
                                  {option.label}
                                </span>
                                <span className="mt-0.5 block text-[11px] leading-4 text-[#86868b]">
                                  {option.description}
                                </span>
                              </span>
                              {isActive ? (
                                <CheckIcon className="size-3.5 shrink-0 text-[#1d1d1f]" />
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
                          handleSelectionChange(
                            GENERIC_TASK_PRODUCT,
                            lastStandardModeRef.current === "research"
                          );
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
                    onChange={setSelectedModel}
                    disabled={disabled}
                    open={modelMenuOpen}
                    onOpenChange={setModelMenuOpen}
                    triggerClassName={toolBtnClassName}
                  />
                </>
              ) : showRoleSelector ? (
                <ChatRoleSelector
                  roles={chatRoles}
                  selectedRole={chatRole}
                  disabled={disabled}
                  onSelect={(role) => onRoleSelect?.(role)}
                />
              ) : null}
            </PromptInputTools>

            <PromptInputTools className="reactor-composer-tools reactor-composer-tools-right ml-auto shrink-0 items-center gap-1.5 self-end">
              <ContextRing
                usage={contextUsage}
                inputChars={question.length}
                contextWindow={currentModelMeta?.contextWindow}
              />
              {busy && onStop ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <button
                      type="button"
                      className="reactor-composer-stop group relative flex size-8 items-center justify-center rounded-full border border-[var(--color-danger-bd)] bg-[var(--color-danger-soft)] p-0 text-[var(--color-danger)] shadow-[var(--shadow-xs)] transition-[background-color,border-color,color,transform] duration-150 hover:border-[var(--color-danger)] hover:bg-[var(--color-danger)] hover:text-white active:scale-[0.92]"
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
                        "bg-[var(--color-accent)] text-[var(--color-text-on-accent)] hover:bg-[var(--color-accent-hover)]",
                        "disabled:cursor-not-allowed disabled:bg-[var(--color-accent)] disabled:text-[var(--color-text-on-accent)] disabled:opacity-45"
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
