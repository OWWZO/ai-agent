import React, { useEffect, useMemo, useRef, useState } from "react";
import { ArrowUpIcon, BookOpenIcon, BrainIcon } from "lucide-react";

import {
  AI_CHAT_FLOATING_CLASS,
} from "@/components/ai-elements/ai-chat-surface";
import {
  PromptInput,
  PromptInputBody,
  PromptInputButton,
  PromptInputFooter,
  PromptInputHeader,
  PromptInputSubmit,
  PromptInputTextarea,
  PromptInputTools,
} from "@/components/ai-elements/prompt-input";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { getOS } from "@/utils";

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
  const { placeholder, showBtn, disabled, size, product, send, dbsShow } =
    props;
  const [question, setQuestion] = useState("");
  const [deepThink, setDeepThink] = useState(false);
  const isChatMode = product?.type === "chat";
  const tempData = useRef<{
    compositing?: boolean;
  }>({});

  useEffect(() => {
    if (isChatMode && deepThink) {
      setDeepThink(false);
    }
  }, [isChatMode, deepThink]);

  const enterTip = useMemo(() => {
    return `Enter 发送，${getOS() === "Mac" ? "Command" : "Ctrl"} + Enter 换行`;
  }, []);

  const canSend = Boolean(question) && !disabled;

  const handleSubmit = ({ text }: { text: string }) => {
    if (!text || disabled) {
      return;
    }

    send({
      message: text,
      outputStyle: product?.type,
      deepThink: isChatMode ? false : deepThink,
    });

    setQuestion("");
  };

  const handleKeyDown: React.KeyboardEventHandler<HTMLTextAreaElement> = (
    event
  ) => {
    if (event.key !== "Enter") {
      return;
    }

    if (tempData.current.compositing || event.nativeEvent.isComposing) {
      return;
    }

    if (event.metaKey || event.ctrlKey) {
      event.preventDefault();
      const textarea = event.currentTarget;
      const { selectionStart, selectionEnd } = textarea;
      const nextValue =
        question.slice(0, selectionStart) +
        "\n" +
        question.slice(selectionEnd);

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
        <PromptInput onSubmit={handleSubmit}>
          {showBtn && product ? (
            <PromptInputHeader className="border-b border-border/50 px-3 pt-3 pb-2">
              <div className="inline-flex items-center gap-2 rounded-full border border-border/60 bg-muted/60 px-3 py-1 text-foreground text-xs">
                <i
                  className={cn(
                    "font_family text-[14px]",
                    product.img,
                    product.color
                  )}
                />
                <span className="font-medium">{product.name}</span>
              </div>
            </PromptInputHeader>
          ) : null}

          <PromptInputBody>
            <PromptInputTextarea
              className={cn(
                "px-3 text-sm leading-6",
                size === "big" ? "min-h-28 pt-3 text-[15px]" : "min-h-20"
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

          <PromptInputFooter className="justify-between gap-3 border-t border-border/50 px-3 pt-2 pb-3">
            {showBtn ? (
              <PromptInputTools className="flex-wrap gap-2">
                {!isChatMode ? (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <PromptInputButton
                        aria-pressed={deepThink}
                        className={cn(
                          "rounded-full px-3",
                          deepThink &&
                            "bg-primary/10 text-primary hover:bg-primary/15 hover:text-primary"
                        )}
                        disabled={disabled}
                        onClick={() => setDeepThink((value) => !value)}
                        size="sm"
                        variant={deepThink ? "secondary" : "ghost"}
                      >
                        <BrainIcon className="size-4" />
                        深度研究
                      </PromptInputButton>
                    </TooltipTrigger>
                    <TooltipContent
                      className={AI_CHAT_FLOATING_CLASS}
                      side="top"
                    >
                      切换深度研究模式
                    </TooltipContent>
                  </Tooltip>
                ) : null}

                {product?.type === "dataAgent" ? (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <PromptInputButton
                        className="rounded-full px-3"
                        disabled={disabled}
                        onClick={() => dbsShow?.(true)}
                        size="sm"
                        variant="ghost"
                      >
                        <BookOpenIcon className="size-4" />
                        知识库
                      </PromptInputButton>
                    </TooltipTrigger>
                    <TooltipContent
                      className={AI_CHAT_FLOATING_CLASS}
                      side="top"
                    >
                      查看知识库
                    </TooltipContent>
                  </Tooltip>
                ) : null}
              </PromptInputTools>
            ) : (
              <div />
            )}

            <PromptInputTools className="shrink-0 gap-2">
              <span className="text-muted-foreground text-xs">{enterTip}</span>
              <Tooltip>
                <TooltipTrigger asChild>
                  <PromptInputSubmit
                    className="rounded-full"
                    disabled={!canSend}
                    variant="default"
                  >
                    <ArrowUpIcon className="size-4" />
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
