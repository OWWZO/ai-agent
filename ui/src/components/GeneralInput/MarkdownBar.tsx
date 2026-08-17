import { Bold, Code2, Heading2, List, ListOrdered } from "lucide-react";

import { cn } from "@/lib/utils";

type Props = {
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
  value: string;
  onChange: (next: string) => void;
  disabled?: boolean;
};

/** 在 textarea 选区插入 Markdown 包裹/前缀（轻量 Aa 工具条，不对齐 Tiptap） */
function wrapSelection(
  value: string,
  start: number,
  end: number,
  before: string,
  after: string,
  placeholder: string
) {
  const selected = value.slice(start, end);
  const body = selected || placeholder;
  const next = value.slice(0, start) + before + body + after + value.slice(end);
  const cursor = start + before.length + body.length + after.length;
  return { next, cursor };
}

function prefixLines(
  value: string,
  start: number,
  end: number,
  prefix: string
) {
  const lineStart = value.lastIndexOf("\n", Math.max(0, start - 1)) + 1;
  const segment = value.slice(lineStart, end);
  const lines = segment.split("\n");
  const rewritten = lines
    .map((line) => (line.startsWith(prefix) ? line : `${prefix}${line || ""}`))
    .join("\n");
  const next = value.slice(0, lineStart) + rewritten + value.slice(end);
  return { next, cursor: lineStart + rewritten.length };
}

const btnClass =
  "inline-flex h-7 w-7 items-center justify-center rounded-md text-[#6b6b70] transition-colors hover:bg-black/[0.04] hover:text-[#1d1d1f] disabled:opacity-40";

const MarkdownBar: ReactorType.FC<Props> = ({
  textareaRef,
  value,
  onChange,
  disabled,
}) => {
  const apply = (fn: (v: string, s: number, e: number) => { next: string; cursor: number }) => {
    const el = textareaRef.current;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    const { next, cursor } = fn(value, start, end);
    onChange(next);
    requestAnimationFrame(() => {
      if (!el) return;
      el.focus();
      el.selectionStart = cursor;
      el.selectionEnd = cursor;
    });
  };

  return (
    <div
      className={cn(
        "flex flex-wrap items-center gap-0.5 border-b border-black/[0.04] px-2.5 py-1"
      )}
    >
      <button
        type="button"
        disabled={disabled}
        className={btnClass}
        title="加粗"
        onClick={() =>
          apply((v, s, e) => wrapSelection(v, s, e, "**", "**", "加粗"))
        }
      >
        <Bold className="size-3.5" />
      </button>
      <button
        type="button"
        disabled={disabled}
        className={btnClass}
        title="二级标题"
        onClick={() =>
          apply((v, s, e) => prefixLines(v, s, e, "## "))
        }
      >
        <Heading2 className="size-3.5" />
      </button>
      <button
        type="button"
        disabled={disabled}
        className={btnClass}
        title="无序列表"
        onClick={() => apply((v, s, e) => prefixLines(v, s, e, "- "))}
      >
        <List className="size-3.5" />
      </button>
      <button
        type="button"
        disabled={disabled}
        className={btnClass}
        title="有序列表"
        onClick={() => apply((v, s, e) => prefixLines(v, s, e, "1. "))}
      >
        <ListOrdered className="size-3.5" />
      </button>
      <button
        type="button"
        disabled={disabled}
        className={btnClass}
        title="行内代码"
        onClick={() =>
          apply((v, s, e) => wrapSelection(v, s, e, "`", "`", "code"))
        }
      >
        <Code2 className="size-3.5" />
      </button>
      <button
        type="button"
        disabled={disabled}
        className={cn(btnClass, "w-auto px-1.5 text-[11px] font-medium")}
        title="代码块"
        onClick={() =>
          apply((v, s, e) =>
            wrapSelection(v, s, e, "```\n", "\n```", "code")
          )
        }
      >
        {"</>"}
      </button>
    </div>
  );
};

export default MarkdownBar;
