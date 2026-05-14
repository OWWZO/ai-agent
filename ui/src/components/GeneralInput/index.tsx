import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { defaultProduct, productList } from "@/utils/constants";
import UploadAttachmentChip from "./UploadAttachmentChip";
import { buildSubmitPayload } from "./inputMode";
import { useAttachmentUploads } from "./useAttachmentUploads";

type Props = {
  sessionId: string;
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
  size: string;
  product?: CHAT.Product;
  deepThink?: boolean;
  displayOutput?: CHAT.Product;
  chatRole?: CHAT.ConversationRole | null;
  chatRoles?: CHAT.FixRole[];
  showRoleSelector?: boolean;
  send: (p: CHAT.TInputInfo) => void;
  onSelectionChange?: (selection: { product: CHAT.Product; deepThink: boolean }) => void;
  onRoleSelect?: (role: CHAT.FixRole) => void;
};

type InputModeKey = "quick" | "think" | "research";
const OUTPUT_TYPES = ["html", "docs", "ppt", "table"];
const OUTPUT_PRODUCTS = productList.filter((item) => OUTPUT_TYPES.includes(item.type)) as CHAT.Product[];
const DATA_AGENT_PRODUCT =
  (productList.find((item) => item.type === "dataAgent") as CHAT.Product | undefined) ?? defaultProduct;
const DEFAULT_OUTPUT_PRODUCT = (OUTPUT_PRODUCTS[0] ?? defaultProduct) as CHAT.Product;

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

const getModeKey = (productType?: string, deepThink = false): InputModeKey => {
  if (productType === "chat") {
    return "quick";
  }
  return deepThink ? "research" : "think";
};

const getOutputProduct = (product?: CHAT.Product, displayOutput?: CHAT.Product) => {
  if (product && OUTPUT_TYPES.includes(product.type)) {
    return product;
  }
  if (displayOutput && OUTPUT_TYPES.includes(displayOutput.type)) {
    return displayOutput;
  }
  return DEFAULT_OUTPUT_PRODUCT;
};

const getProductLabel = (name: string) => name.replace("模式", "");
const getOutputShortDescription = (type: string) => {
  switch (type) {
    case "html":
      return "网页页面";
    case "docs":
      return "文档报告";
    case "ppt":
      return "演示文稿";
    case "table":
      return "数据表格";
    default:
      return "结构化输出";
  }
};

type SelectorTone = {
  icon: string;
  iconActive: string;
  check: string;
};

const MODE_TONES: Record<InputModeKey, SelectorTone> = {
  quick: {
    icon: "text-[#4b5563]",
    iconActive: "bg-[#e8f2ff] text-[#0a74da]",
    check: "text-[#0a74da]",
  },
  think: {
    icon: "text-[#4b5563]",
    iconActive: "bg-[#e8f2ff] text-[#0a74da]",
    check: "text-[#0a74da]",
  },
  research: {
    icon: "text-[#4b5563]",
    iconActive: "bg-[#e8f2ff] text-[#0a74da]",
    check: "text-[#0a74da]",
  },
};

const OUTPUT_TONES: Record<string, SelectorTone> = {
  html: {
    icon: "text-[#4b5563]",
    iconActive: "bg-[#e8f2ff] text-[#0a74da]",
    check: "text-[#0a74da]",
  },
  docs: {
    icon: "text-[#4b5563]",
    iconActive: "bg-[#e8f2ff] text-[#0a74da]",
    check: "text-[#0a74da]",
  },
  ppt: {
    icon: "text-[#4b5563]",
    iconActive: "bg-[#e8f2ff] text-[#0a74da]",
    check: "text-[#0a74da]",
  },
  table: {
    icon: "text-[#4b5563]",
    iconActive: "bg-[#e8f2ff] text-[#0a74da]",
    check: "text-[#0a74da]",
  },
};

const DATA_AGENT_TONE: SelectorTone = {
  icon: "text-[#4b5563]",
  iconActive: "bg-[#e8f2ff] text-[#0a74da]",
  check: "text-[#0a74da]",
};

const selectorTrayClassName =
  "flex min-w-0 flex-1 flex-wrap items-center gap-1 rounded-full bg-transparent p-0.5 sm:flex-none";

const chipButtonClassName = (active: boolean, disabled?: boolean) =>
  cn(
    "group inline-flex h-9 max-w-full items-center gap-2 rounded-full border border-transparent px-3 pr-3 text-[14px] font-medium transition-all duration-200",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#b9d9ff] focus-visible:ring-offset-2 focus-visible:ring-offset-white",
    disabled && "cursor-not-allowed opacity-50",
    !disabled && "hover:bg-white",
    active
      ? "bg-[#e8f2ff] text-[#0a74da]"
      : "bg-transparent text-[#111827]"
  );

const chipIconWrapClassName = (tone: SelectorTone, active: boolean) =>
  cn(
    "flex size-[26px] shrink-0 items-center justify-center rounded-full transition-all duration-200",
    active
      ? tone.iconActive
      : cn("bg-transparent ring-0 group-hover:bg-white/90", tone.icon)
  );

const menuContentClassName =
  "rounded-[16px] border-0 bg-white p-0.5 shadow-[0_10px_28px_-18px_rgba(15,23,42,0.2)]";

const menuTitleClassName =
  "px-2 pb-1 pt-0.5 text-[10px] font-semibold tracking-[0.06em] text-[#6b7280] uppercase";

const menuItemClassName = (active: boolean) =>
  cn(
    "flex w-full gap-1.5 rounded-xl border border-transparent px-1.5 py-2 text-left transition-all duration-200",
    active
      ? "bg-[#e8f2ff]"
      : "bg-transparent hover:bg-[#f9fafb]"
  );

const menuIconWrapClassName = (tone: SelectorTone, active: boolean) =>
  cn(
    "mt-0.5 flex size-6.5 shrink-0 items-center justify-center rounded-lg transition-all duration-200",
    active
      ? tone.iconActive
      : cn("bg-transparent ring-0", tone.icon)
  );

const GeneralInput: ReactorType.FC<Props> = (props) => {
  const {
    sessionId,
    placeholder,
    showBtn,
    disabled,
    size,
    product,
    deepThink = false,
    displayOutput,
    chatRole,
    chatRoles = [],
    showRoleSelector = false,
    send,
    onSelectionChange,
    onRoleSelect,
  } = props;

  const [question, setQuestion] = useState("");
  const [modeMenuOpen, setModeMenuOpen] = useState(false);
  const [outputMenuOpen, setOutputMenuOpen] = useState(false);
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
  const resolvedOutputProduct = useMemo(
    () => getOutputProduct(product, displayOutput),
    [displayOutput, product]
  );

  // 记住上一次标准任务模式，切到“数据分析”后仍能保持用户刚才的选择感。
  const lastStandardModeRef = useRef<InputModeKey>(currentMode === "quick" ? "think" : currentMode);
  const lastOutputProductRef = useRef<CHAT.Product>(resolvedOutputProduct);

  useEffect(() => {
    if (product?.type === "dataAgent") {
      return;
    }

    lastStandardModeRef.current = currentMode;
    lastOutputProductRef.current = resolvedOutputProduct;
  }, [currentMode, product?.type, resolvedOutputProduct]);

  const visibleMode = isDataAgent ? lastStandardModeRef.current : currentMode;
  const visibleOutputProduct = isDataAgent ? lastOutputProductRef.current : resolvedOutputProduct;
  const currentModeOption = MODE_OPTIONS.find((item) => item.key === visibleMode) ?? MODE_OPTIONS[0];
  const CurrentModeIcon = currentModeOption.icon;
  const currentModeTone = MODE_TONES[currentModeOption.key];
  const visibleOutputTone = OUTPUT_TONES[visibleOutputProduct.type] ?? OUTPUT_TONES.html;
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
    !hasUploadingAttachment &&
    !hasFailedAttachment;
  const showOutputSelector = showBtn && visibleMode !== "quick";
  const showDataAgentToggle = showBtn && (isDataAgent || visibleMode !== "quick");

  const handleAttachmentsAdded = useCallback((attachments: PromptInputAttachmentItem[]) => {
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
  }, [addAttachmentUploads]);

  const handleSelectionChange = (nextProduct: CHAT.Product, nextDeepThink: boolean) => {
    onSelectionChange?.({
      product: nextProduct,
      deepThink: nextDeepThink,
    });
  };

  const handleModeSelect = (modeKey: InputModeKey) => {
    // 前端暂时关闭对话模式切换，菜单里不再暴露 quick。
    if (modeKey === "quick") {
      setModeMenuOpen(false);
      return;
    }
    handleSelectionChange(visibleOutputProduct, modeKey === "research");
    setModeMenuOpen(false);
  };

  const handleOutputSelect = (nextOutput: CHAT.Product) => {
    handleSelectionChange(nextOutput, visibleMode === "research");
    setOutputMenuOpen(false);
  };

  const handleSubmit = ({ text }: { text: string; files: unknown[] }) => {
    if (!text.trim() || disabled || hasUploadingAttachment || hasFailedAttachment) return;

    send(
      buildSubmitPayload({
        question: text,
        visibleMode,
        isDataAgent,
        visibleOutputProduct,
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
      const textarea = event.currentTarget;
      const { selectionStart, selectionEnd } = textarea;
      const nextValue =
        question.slice(0, selectionStart) + "\n" + question.slice(selectionEnd);
      setQuestion(nextValue);
      requestAnimationFrame(() => {
        textarea.selectionStart = selectionStart + 1;
        textarea.selectionEnd = selectionStart + 1;
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
          accept="image/*,application/pdf,.txt,.md,.csv,.xlsx,.docx"
          className={cn(
            "reactor-input-flat w-full rounded-[24px] transition-all duration-300",
            size === "big" ? "rounded-[28px]" : "rounded-[22px]"
          )}
          convertBlobUrlsOnSubmit={false}
          multiple
          onAttachmentsAdded={handleAttachmentsAdded}
          onSubmit={handleSubmit}
        >
          <PromptInputBody>
            <PromptInputAttachments className="px-4 pt-3">
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
                "px-4 text-[14px] leading-6 text-[var(--chat-text)] placeholder:text-[var(--chat-text-soft)] placeholder:opacity-100",
                "focus:placeholder:text-[var(--chat-text-soft)]/50",
                size === "big" ? "min-h-24 pt-4 text-[15px]" : "min-h-16 pt-3.5"
              )}
              disabled={disabled}
              onChange={(event) => setQuestion(event.target.value)}
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
          </PromptInputBody>

          <PromptInputFooter
            className={cn(
              "items-end gap-3 px-3.5 pb-3 pt-1",
              showBtn ? "flex-col sm:flex-row sm:items-end" : "justify-between gap-2.5"
            )}
          >
            <PromptInputTools
              className={cn(
                "w-full flex-wrap items-center gap-2",
                !showBtn && "w-auto gap-1.5"
              )}
            >
              <PromptInputActionMenu>
                <PromptInputActionMenuTrigger
                  size="icon-sm"
                  variant="ghost"
                  disabled={disabled}
                  className="rounded-full border-0 bg-white text-[#111827] shadow-none ring-0 transition-all duration-200 hover:bg-[#f9fafb] focus-visible:ring-0"
                >
                  <PlusIcon className="size-5" />
                </PromptInputActionMenuTrigger>
                <PromptInputActionMenuContent className={cn("min-w-[180px]", menuContentClassName)}>
                  <PromptInputActionAddAttachments label="上传附件" />
                </PromptInputActionMenuContent>
              </PromptInputActionMenu>

              {showBtn ? (
                <div className={selectorTrayClassName}>
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
                        aria-pressed={true}
                        disabled={disabled}
                        className={chipButtonClassName(true, disabled)}
                      >
                        <span className={chipIconWrapClassName(currentModeTone, true)}>
                          <CurrentModeIcon className="size-4" />
                        </span>
                        <span className="truncate">{currentModeOption.label}</span>
                        <ChevronDownIcon
                          className={cn("size-4 shrink-0 text-[var(--chat-text-muted)] transition-transform", modeMenuOpen && "rotate-180")}
                        />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent
                      align="start"
                      side="bottom"
                      sideOffset={12}
                      className={cn("w-[190px]", menuContentClassName)}
                    >
                      <div className={menuTitleClassName}>推理模式</div>
                      <div className="space-y-1">
                        {VISIBLE_MODE_OPTIONS.map((option) => {
                          const isActive = option.key === visibleMode;
                          const tone = MODE_TONES[option.key];
                          return (
                            <button
                              key={option.key}
                              type="button"
                              className={menuItemClassName(isActive)}
                              onClick={() => handleModeSelect(option.key)}
                            >
                              <span className={menuIconWrapClassName(tone, isActive)}>
                                <option.icon className="size-3.5" />
                              </span>
                              <span className="min-w-0 flex-1 pr-0.5">
                                <span className="block text-[14px] font-medium tracking-[-0.01em] text-[var(--chat-text)]">
                                  {option.label}
                                </span>
                                <span className="mt-0.5 block text-[11px] leading-4 text-[var(--chat-text-soft)]">
                                  {option.description}
                                </span>
                              </span>
                              {isActive ? <CheckIcon className={cn("mt-1 size-3 shrink-0", tone.check)} /> : null}
                            </button>
                          );
                        })}
                      </div>
                    </DropdownMenuContent>
                  </DropdownMenu>

                  {showOutputSelector ? (
                    <DropdownMenu open={outputMenuOpen} onOpenChange={setOutputMenuOpen}>
                      <DropdownMenuTrigger asChild>
                        <button
                          type="button"
                          aria-pressed={!isDataAgent}
                          disabled={disabled || isDataAgent}
                          className={chipButtonClassName(!isDataAgent, disabled || isDataAgent)}
                        >
                          <span className={chipIconWrapClassName(visibleOutputTone, !isDataAgent)}>
                            <i className={cn("font_family text-[14px]", visibleOutputProduct.img)} />
                          </span>
                          <span className="truncate">{getProductLabel(visibleOutputProduct.name)}</span>
                          <ChevronDownIcon
                            className={cn("size-4 shrink-0 text-[var(--chat-text-muted)] transition-transform", outputMenuOpen && "rotate-180")}
                          />
                        </button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent
                        align="start"
                        side="bottom"
                        avoidCollisions={false}
                        sideOffset={12}
                        className={cn("w-[186px]", menuContentClassName)}
                      >
                        <div className={menuTitleClassName}>输出格式</div>
                        <div className="space-y-1">
                          {OUTPUT_PRODUCTS.map((item) => {
                            const isActive = item.type === visibleOutputProduct.type && !isDataAgent;
                            const tone = OUTPUT_TONES[item.type] ?? visibleOutputTone;
                            return (
                              <button
                                key={item.type}
                                type="button"
                                className={menuItemClassName(isActive)}
                                onClick={() => handleOutputSelect(item)}
                              >
                                <span className={menuIconWrapClassName(tone, isActive)}>
                                  <i className={cn("font_family text-[13px]", item.img)} />
                                </span>
                                <span className="min-w-0 flex-1 pr-0.5">
                                  <span className="block text-[14px] font-medium tracking-[-0.01em] text-[var(--chat-text)]">
                                    {getProductLabel(item.name)}
                                  </span>
                                  <span className="mt-0.5 block text-[11px] leading-4 text-[var(--chat-text-soft)]">
                                    {getOutputShortDescription(item.type)}
                                  </span>
                                </span>
                                {isActive ? <CheckIcon className={cn("size-3 shrink-0", tone.check)} /> : null}
                              </button>
                            );
                          })}
                        </div>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  ) : null}

                  {showDataAgentToggle ? (
                    <button
                      type="button"
                      aria-pressed={isDataAgent}
                      disabled={disabled}
                      className={chipButtonClassName(isDataAgent, disabled)}
                      onClick={() => {
                        if (visibleMode === "quick" && !isDataAgent) return;
                        handleSelectionChange(DATA_AGENT_PRODUCT, false);
                      }}
                    >
                      <span className={chipIconWrapClassName(DATA_AGENT_TONE, isDataAgent)}>
                        <BarChart3Icon className="size-4" />
                      </span>
                      <span className="truncate">数据分析</span>
                    </button>
                  ) : null}
                </div>
              ) : showRoleSelector ? (
                <ChatRoleSelector
                  roles={chatRoles}
                  selectedRole={chatRole}
                  disabled={disabled}
                  onSelect={(role) => onRoleSelect?.(role)}
                />
              ) : null}
            </PromptInputTools>

            <PromptInputTools className="shrink-0 gap-2 self-end">
              <Tooltip>
                <TooltipTrigger asChild>
                  <PromptInputSubmit
                    className="size-9 rounded-full bg-[var(--chat-text)] text-[var(--chat-surface)] shadow-md transition-all duration-300 hover:scale-105 hover:shadow-lg hover:shadow-[var(--primary)]/15 disabled:bg-[var(--chat-surface-muted)] disabled:text-[var(--chat-text-muted)] disabled:shadow-none disabled:scale-100"
                    disabled={!canSend}
                    variant="default"
                  >
                    <ArrowUpIcon className="size-5" />
                  </PromptInputSubmit>
                </TooltipTrigger>
                <TooltipContent className={AI_CHAT_FLOATING_CLASS} side="top">
                  发送
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
