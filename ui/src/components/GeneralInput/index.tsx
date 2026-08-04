import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowUpIcon,
  BarChart3Icon,
  BrainCircuitIcon,
  CheckIcon,
  ChevronDownIcon,
  PlusIcon,
  SearchIcon,
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
import { buildSubmitPayload } from "./inputMode";
import { useAttachmentUploads } from "./useAttachmentUploads";

type Props = {
  sessionId: string;
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
  /** Agent 任务进行中时，在发送按钮旁展示轻量运行指示 */
  busy?: boolean;
  /** 任务进行中点击发送区停止本轮 */
  onStop?: () => void;
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
    "inline-flex h-8 max-w-full items-center gap-1 rounded-full px-2 text-[13px] font-medium tracking-[-0.01em] transition-colors",
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
    busy = false,
    onStop,
    size,
    product,
    deepThink = false,
    chatRole,
    chatRoles = [],
    showRoleSelector = false,
    send,
    onSelectionChange,
    onRoleSelect,
  } = props;

  const [question, setQuestion] = useState("");
  const [modeMenuOpen, setModeMenuOpen] = useState(false);
  const tempData = useRef<{ compositing?: boolean }>({});
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
  const canSend =
    Boolean(question.trim()) &&
    !disabled &&
    !busy &&
    !hasUploadingAttachment &&
    !hasFailedAttachment;
  const showDataAgentToggle = showBtn && (isDataAgent || visibleMode !== "quick");

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

    send(
      buildSubmitPayload({
        question: trimmed,
        visibleMode,
        isDataAgent,
        currentProductType: product?.type,
        uploadedFiles,
        chatRole: chatRole || null,
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

    if (!canSend) {
      event.preventDefault();
      return;
    }

    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  };

  return (
    <TooltipProvider>
      <div className="w-full">
        <PromptInput
          accept={ATTACHMENT_ACCEPT}
          className={cn(
            "reactor-input-flat w-full transition-[border-color,box-shadow] duration-200",
            size === "big" ? "rounded-[28px]" : "rounded-[26px]"
          )}
          convertBlobUrlsOnSubmit={false}
          multiple
          onAttachmentsAdded={handleAttachmentsAdded}
          onSubmit={handleSubmit}
        >
          <PromptInputBody>
            <PromptInputAttachments className="px-3.5 pt-2.5">
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

            <PromptInputTextarea
              className={cn(
                "px-4 text-[15px] leading-[1.55] text-[#1d1d1f] placeholder:text-[#aeaeb2] placeholder:opacity-100",
                size === "big" ? "min-h-[76px] pt-3.5" : "min-h-[52px] pt-2.5"
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

          <PromptInputFooter className="items-center justify-between gap-2 px-2.5 pb-2 pt-0.5">
            <PromptInputTools className="min-w-0 flex-1 flex-wrap items-center gap-0.5">
              <PromptInputActionMenu>
                <PromptInputActionMenuTrigger
                  size="icon-sm"
                  variant="ghost"
                  disabled={disabled}
                  className="h-8 w-8 rounded-full border-0 bg-transparent text-[#6b6b70] shadow-none ring-0 hover:bg-black/[0.04] hover:text-[#1d1d1f] focus-visible:ring-0"
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

            <PromptInputTools className="ml-auto shrink-0 items-center gap-1.5 self-end">
              {busy ? (
                <div
                  className="mr-0.5 flex items-center text-[12px] font-medium text-[#86868b]"
                  role="status"
                  aria-live="polite"
                  aria-label="Working"
                >
                  <span className="thinking-shimmer text-[12px] font-medium tracking-[0.02em]">
                    Working
                  </span>
                </div>
              ) : null}
              <Tooltip>
                <TooltipTrigger asChild>
                  {busy && onStop ? (
                    <button
                      type="button"
                      className="relative flex size-8 items-center justify-center rounded-full border-0 bg-[#1d1d1f] p-0 text-white shadow-none transition-opacity hover:opacity-90"
                      onClick={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        onStop();
                      }}
                      aria-label="停止"
                    >
                      <span className="block size-2.5 rounded-[2px] bg-white" />
                    </button>
                  ) : (
                    <PromptInputSubmit
                      className={cn(
                        "reactor-send-btn relative flex size-8 items-center justify-center rounded-full border-0 p-0 shadow-none transition-opacity",
                        "bg-[#1d1d1f] text-white hover:opacity-90",
                        "disabled:cursor-not-allowed disabled:bg-[#e8e8ed] disabled:text-[#aeaeb2] disabled:opacity-100"
                      )}
                      disabled={!canSend}
                      variant="ghost"
                    >
                      <ArrowUpIcon className="size-[15px] stroke-[2.25]" />
                    </PromptInputSubmit>
                  )}
                </TooltipTrigger>
                <TooltipContent className={AI_CHAT_FLOATING_CLASS} side="top">
                  {busy ? (onStop ? "停止" : "任务进行中") : "发送"}
                </TooltipContent>
              </Tooltip>
            </PromptInputTools>
          </PromptInputFooter>
        </PromptInput>
      </div>
    </TooltipProvider>
  );
};

export default GeneralInput;
