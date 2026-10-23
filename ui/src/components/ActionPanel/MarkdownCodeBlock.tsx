import type { ReactNode } from "react";

import {
  DiffCodeFence,
  KimiCodeFence,
} from "@/components/Dialogue/markdown/KimiCodeFence";
import MermaidBlock from "./MermaidBlock";
import type { BundledLanguage } from "shiki";

import { cn } from "@/lib/utils";

/** 终答 Markdown 的代码围栏渲染器形状（`react-markdown` 的 `components.code`）。 */
export type MarkdownCodeBlockProps = {
  inline?: boolean;
  className?: string;
  children?: unknown;
};

function childrenToText(children: unknown): string {
  if (children == null || typeof children === "boolean") {
    return "";
  }
  if (typeof children === "string" || typeof children === "number") {
    return String(children);
  }
  if (Array.isArray(children)) {
    return children.map(childrenToText).join("");
  }
  if (typeof children === "object" && "props" in children) {
    return childrenToText(
      (children as { props?: { children?: unknown } }).props?.children
    );
  }
  return "";
}

function cnInlineCode(className?: string) {
  return cn("kimi-inline-code", className);
}

/**
 * 代码围栏渲染器工厂。
 *
 * 做成工厂而不是模块级常量，是因为 `streaming` 必须来自当前渲染上下文 ——
 * 流式期间要跳过 shiki 高亮与 Mermaid（对齐 ZCode
 * `enableSyntaxHighlighting={!renderStreaming}` / `renderMermaid={!renderStreaming}`）。
 */
export const createCodeBlock =
  (streaming: boolean): React.FC<MarkdownCodeBlockProps> =>
    function CodeBlock({ inline, className, children, ...rest }) {
      // Streamdown 用 data-block 标记围栏；react-markdown 用 inline=false
      const isBlock =
        !inline || Object.prototype.hasOwnProperty.call(rest, "data-block");
      const match = /language-(\w+)/.exec(className || "");
      const trimmed = childrenToText(children).replace(/\n$/, "");

      // Mermaid 解析 + 渲染比 shiki 更重，流式时先按普通代码块展示。
      if (match?.[1] === "mermaid" && !streaming) {
        return <MermaidBlock>{children as ReactNode}</MermaidBlock>;
      }

      if (isBlock && match?.[1] === "diff") {
        return <DiffCodeFence code={trimmed} />;
      }

      if (isBlock && match) {
        const rawLang = match[1];
        const safeLanguage = rawLang as BundledLanguage;
        return (
          <KimiCodeFence
            code={trimmed}
            language={safeLanguage}
            streaming={streaming}
          />
        );
      }

      // 无 language 的多行 fence：仍按代码块渲染，避免掉进行内 chip
      if (isBlock && trimmed.includes("\n")) {
        return (
          <KimiCodeFence code={trimmed} language="text" streaming={streaming} />
        );
      }

      return (
        <code className={cnInlineCode(className)}>{children as ReactNode}</code>
      );
    };
