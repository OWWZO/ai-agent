import React, { useEffect, useRef, useState } from "react";
import { ArrowUpIcon, BookOpenIcon, BrainIcon, PlusIcon } from "lucide-react";
import type { FileUIPart } from "ai";

import {
  AI_CHAT_FLOATING_CLASS,
} from "@/components/ai-elements/ai-chat-surface";
import {
  PromptInput,
  PromptInputBody,
  PromptInputButton,
  PromptInputFooter,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
  PromptInputActionMenu,
  PromptInputActionMenuTrigger,
  PromptInputActionMenuContent,
  PromptInputActionAddAttachments,
  PromptInputAttachments,
  PromptInputAttachment,
} from "@/components/ai-elements/prompt-input";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

type Props = {
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
  size: string;
  product?: CHAT.Product;
  send: (p: CHAT.TInputInfo) => void;
  dbsShow?: (show: boolean) => void;
};

const GeneralInput: GenieType.FC<Props> = (props) => {
  const { placeholder, showBtn, disabled, size, product, send, dbsShow } = props;
  const [question, setQuestion] = useState("");
  const [deepThink, setDeepThink] = useState(false);
  const isChatMode = product?.type === "chat";
  const tempData = useRef<{ compositing?: boolean }>({});

  useEffect(() => {
    if (isChatMode && deepThink) {
      setDeepThink(false);
    }
  }, [isChatMode, deepThink]);

  const canSend = Boolean(question) && !disabled;

  const handleSubmit = ({ text, files }: { text: string; files: FileUIPart[] }) => {
    if (!text || disabled) return;

    const mappedFiles: CHAT.TFile[] = files.map((f) => ({
      name: f.filename || "",
      url: f.url ?? "",
      type: f.mediaType || "",
      size: 0,
    }));

    send({
      message: text,
      outputStyle: product?.type,
      deepThink: isChatMode ? false : deepThink,
      files: mappedFiles.length > 0 ? mappedFiles : undefined,
    });

    setQuestion("");
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
            "w-full rounded-[28px] bg-[var(--chat-surface)]/95 shadow-[var(--shadow-sm)] transition-all duration-300 focus-within:shadow-[var(--shadow-md)]",
            size === "big" ? "rounded-[30px]" : "rounded-[24px]"
          )}
          multiple
          onSubmit={handleSubmit}
        >
          <PromptInputBody>
            {/* 已选附件列表 */}
            <PromptInputAttachments className="px-4 pt-3">
              {(file) => <PromptInputAttachment key={file.id} data={file} />}
            </PromptInputAttachments>

            <PromptInputTextarea
              className={cn(
                "px-5 text-[15px] leading-7 text-[var(--chat-text)] placeholder:text-[var(--chat-text-muted)] placeholder:italic",
                "focus:placeholder:text-[var(--chat-text-soft)]/50",
                size === "big" ? "min-h-28 pt-5 text-[16px]" : "min-h-16 pt-4"
              )}
              disabled={disabled}
              onChange={(event) => setQuestion(event.target.value)}
              onCompositionEnd={() => { tempData.current.compositing = false; }}
              onCompositionStart={() => { tempData.current.compositing = true; }}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              value={question}
            />
          </PromptInputBody>

          <PromptInputFooter className="justify-between gap-3 px-4 pb-4 pt-1">
            {/* 左侧：+ 按钮 + 深度研究 + 知识库 */}
            <PromptInputTools className="flex-wrap gap-1.5">
              {/* + 附件按钮（始终显示） */}
              <PromptInputActionMenu>
                <PromptInputActionMenuTrigger>
                  <PromptInputButton
                    size="icon-sm"
                    variant="ghost"
                    disabled={disabled}
                    className="rounded-full bg-[var(--chat-surface-muted)] text-[var(--chat-text-soft)] transition-all duration-300 hover:bg-[var(--chat-surface)] hover:text-[var(--chat-text)] hover:shadow-md hover:scale-105"
                  >
                    <PlusIcon className="size-5" />
                  </PromptInputButton>
                </PromptInputActionMenuTrigger>
                <PromptInputActionMenuContent>
                  <PromptInputActionAddAttachments label="上传附件" />
                </PromptInputActionMenuContent>
              </PromptInputActionMenu>

              {/* 深度研究（非聊天模式时显示） */}
              {showBtn && !isChatMode ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <PromptInputButton
                      aria-pressed={deepThink}
                      className={cn(
                        "rounded-full px-3.5 py-1.5 text-[13px] font-medium transition-all duration-300",
                        "flex items-center gap-1.5",
                        deepThink
                          ? "bg-[var(--primary)] text-white shadow-md shadow-[var(--primary)]/20 hover:bg-[var(--primary)]/90"
                          : "bg-[var(--chat-surface-muted)] text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface)] hover:text-[var(--chat-text)] hover:shadow-md"
                      )}
                      disabled={disabled}
                      onClick={() => setDeepThink((v) => !v)}
                      size="sm"
                      variant="ghost"
                    >
                      <BrainIcon className="size-4" />
                      深度研究
                    </PromptInputButton>
                  </TooltipTrigger>
                  <TooltipContent className={AI_CHAT_FLOATING_CLASS} side="top">
                    切换深度研究模式
                  </TooltipContent>
                </Tooltip>
              ) : null}

              {/* 知识库（仅首页 dataAgent 模式） */}
              {showBtn && product?.type === "dataAgent" ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <PromptInputButton
                      className="rounded-full bg-[var(--chat-surface-muted)] px-3.5 py-1.5 text-[13px] font-medium text-[var(--chat-text-soft)] transition-all duration-300 hover:bg-[var(--chat-surface)] hover:text-[var(--chat-text)] hover:shadow-md flex items-center gap-1.5"
                      disabled={disabled}
                      onClick={() => dbsShow?.(true)}
                      size="sm"
                      variant="ghost"
                    >
                      <BookOpenIcon className="size-4" />
                      知识库
                    </PromptInputButton>
                  </TooltipTrigger>
                  <TooltipContent className={AI_CHAT_FLOATING_CLASS} side="top">
                    查看知识库
                  </TooltipContent>
                </Tooltip>
              ) : null}
            </PromptInputTools>

            {/* 右侧：发送按钮 */}
            <PromptInputTools className="shrink-0 gap-2">
              <Tooltip>
                <TooltipTrigger asChild>
                  <PromptInputSubmit
                    className="size-10 rounded-full bg-[var(--chat-text)] text-[var(--chat-surface)] shadow-md transition-all duration-300 hover:scale-105 hover:shadow-lg hover:shadow-[var(--primary)]/15 disabled:bg-[var(--chat-surface-muted)] disabled:text-[var(--chat-text-muted)] disabled:shadow-none disabled:scale-100"
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
