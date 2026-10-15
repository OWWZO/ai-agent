import { useCallback, useEffect, useRef, useState } from "react";
import { CheckIcon, CopyIcon } from "lucide-react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { copyText } from "@/utils";

/**
 * 失败 / 停止状态词的富提示卡（对齐 ZCode `ToolLayout.tsx:240-293`）。
 *
 * ZCode 不再把失败原因挂在原生 `title` 上：原生提示无法换行、无法复制、
 * 长报错会被系统裁成一行。这里改成悬停浮层 —— 最多 3 行错误原文 + 一个复制按钮。
 * 触发词保持虚线 + `cursor: help`，与成功态一致的展开逻辑不变。
 */
export function ToolFailureTip({ label, text }: { label: string; text: string }) {
  const [copied, setCopied] = useState(false);
  const resetTimerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (resetTimerRef.current !== null) {
        window.clearTimeout(resetTimerRef.current);
      }
    };
  }, []);

  const handleCopy = useCallback(
    (event: { preventDefault: () => void; stopPropagation: () => void }) => {
      event.preventDefault();
      event.stopPropagation();
      if (!text.trim()) {
        return;
      }
      copyText(text);
      setCopied(true);
      if (resetTimerRef.current !== null) {
        window.clearTimeout(resetTimerRef.current);
      }
      resetTimerRef.current = window.setTimeout(() => {
        setCopied(false);
        resetTimerRef.current = null;
      }, 1500);
    },
    [text]
  );

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="kimi-tool-row-status">{label}</span>
        </TooltipTrigger>
        <TooltipContent side="top" align="start" className="kimi-tool-fail-tip">
          <span className="kimi-tool-fail-tip-text">{text}</span>
          <button
            type="button"
            className="kimi-tool-fail-tip-copy"
            aria-label={copied ? "已复制错误信息" : "复制错误信息"}
            title={copied ? "已复制" : "复制"}
            onClick={handleCopy}
          >
            {copied ? <CheckIcon className="size-3" /> : <CopyIcon className="size-3" />}
          </button>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
