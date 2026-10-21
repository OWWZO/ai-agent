import { useState, type ReactNode } from "react";
import { ChevronRightIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { ToolArgStreamPreview } from "./ToolArgStreamPreview";

type ToolArgStreamCardProps = {
  icon: ReactNode;
  /** 类别标签（ZCode `kindLabel`）：Bash / Read / Edit … */
  name: string;
  /** 摘要行补充说明：路径 / 命令前缀 */
  summary?: string;
  /** 摘要行尾部的参数统计 chip */
  chip?: string;
  /** 后端累计入参（可能尚未形成合法 JSON） */
  text: string;
  /** 入参是否仍在流式生成 */
  streaming: boolean;
  testId?: string;
};

/**
 * 工具入参流卡：工具执行期间铺在时间线里的入参预览。
 *
 * 默认**折叠** —— 入参属于调用细节，用户先看「调用了什么」，要看传参再点头部展开。
 * 这与工具执行完成后的卡片折叠态是同一口径（参数永远不自己摊开）。
 *
 * 头部整行可点：与工具卡（`ToolRow`）的展开手势一致，右箭头同步旋转。
 */
export function ToolArgStreamCard({
  icon,
  name,
  summary = "",
  chip = "",
  text,
  streaming,
  testId,
}: ToolArgStreamCardProps) {
  const [open, setOpen] = useState(false);

  return (
    <div
      className={cn("kimi-tool-arg-stream", open && "is-open")}
      data-testid={testId}
    >
      <button
        type="button"
        className="kimi-tool-arg-stream-head"
        aria-expanded={open}
        aria-label={open ? "收起工具入参" : "展开工具入参"}
        onClick={() => setOpen((v) => !v)}
      >
        <span className="kimi-tool-arg-stream-glyph">{icon}</span>
        <span className="kimi-tool-arg-stream-name">{name}</span>
        {summary ? (
          <span className="kimi-tool-arg-stream-meta" title={summary}>
            {summary}
          </span>
        ) : null}
        {chip ? <span className="kimi-tool-arg-stream-chip">{chip}</span> : null}
        <ChevronRightIcon
          className={cn(
            "kimi-tool-arg-stream-chevron size-3.5 shrink-0 transition-transform duration-200 ease-out",
            open ? "rotate-90" : "rotate-0"
          )}
        />
      </button>
      {open ? (
        <ToolArgStreamPreview
          text={text}
          label="参数"
          streaming={streaming}
          prominent
        />
      ) : null}
    </div>
  );
}
