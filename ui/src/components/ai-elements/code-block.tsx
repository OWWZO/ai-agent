"use client";

import { Button } from "@/components/ui/button";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import { cn } from "@/lib/utils";
import { CheckIcon, CopyIcon } from "lucide-react";
import {
  type ComponentProps,
  createContext,
  type HTMLAttributes,
  useContext,
  useEffect,
  useState,
} from "react";
import type { BundledLanguage, ShikiTransformer } from "shiki";
import { loadShiki } from "@/lib/lazy/shiki";

type CodeBlockProps = HTMLAttributes<HTMLDivElement> & {
  code: string;
  language: BundledLanguage;
  showLineNumbers?: boolean;
};

type CodeBlockContextType = {
  code: string;
};

const TEXT_LANGUAGE = "text" as BundledLanguage;

const CodeBlockContext = createContext<CodeBlockContextType>({
  code: "",
});

const lineNumberTransformer: ShikiTransformer = {
  name: "line-numbers",
  line(node, line) {
    node.children.unshift({
      type: "element",
      tagName: "span",
      properties: {
        className: [
          "inline-block",
          "min-w-10",
          "mr-4",
          "text-right",
          "select-none",
          "text-muted-foreground",
        ],
      },
      children: [{ type: "text", value: String(line) }],
    });
  },
};

export async function highlightCode(
  code: string,
  language: BundledLanguage,
  showLineNumbers = false
) {
  const { codeToHtml } = await loadShiki();
  // 同时生成浅色和深色主题，调用方可按主题切换而无需重复执行 Shiki。
  const transformers: ShikiTransformer[] = showLineNumbers
    ? [lineNumberTransformer]
    : [];

  const render = (lang: BundledLanguage) =>
    Promise.all([
      codeToHtml(code, {
        lang,
        theme: "github-light",
        transformers,
      }),
      codeToHtml(code, {
        lang,
        theme: "github-dark",
        transformers,
      }),
    ]);

  try {
    // 与 kimi-web Markdown.vue 一致：github-light / github-dark
    return await render(language);
  } catch (error) {
    if (language === TEXT_LANGUAGE) throw error;
    return await render(TEXT_LANGUAGE);
  }
}

export const CodeBlock = ({
  code,
  language,
  showLineNumbers = false,
  className,
  children,
  ...props
}: CodeBlockProps) => {
  const [html, setHtml] = useState<string>("");
  const [highlightError, setHighlightError] = useState(false);

  useEffect(() => {
    // code/language 变化会重新高亮；清理函数撤销本轮结果的状态写入资格。
    let cancelled = false;
    setHtml("");
    setHighlightError(false);
    void highlightCode(code, language, showLineNumbers)
      .then(([light]) => {
        if (!cancelled) {
          setHtml(light);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setHighlightError(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [code, language, showLineNumbers]);

  return (
    <CodeBlockContext.Provider value={{ code }}>
      <ViewerPanelShell
        className={cn("group w-full text-foreground", className)}
        headerRight={children}
        label={language.toUpperCase()}
        subtitle="Source"
        {...props}
      >
        {highlightError ? (
          <pre className="max-h-[min(70vh,560px)] overflow-auto whitespace-pre-wrap break-words px-3 py-2.5 font-mono text-sm sm:px-4 sm:py-3">
            {code}
          </pre>
        ) : (
          <div
            className="max-h-[min(70vh,560px)] overflow-auto rounded-lg px-3 py-2.5 sm:px-4 sm:py-3 shadow-[inset_0_1px_0_oklch(1_0_0_/_0.06)] dark:shadow-[inset_0_1px_0_oklch(1_0_0_/_0.04)] [&>pre]:m-0 [&>pre]:bg-transparent! [&>pre]:p-0 [&>pre]:text-foreground! [&>pre]:text-sm [&>pre]:!whitespace-pre-wrap [&>pre]:break-words [&>pre]:[overflow-wrap:anywhere] [&_code]:font-mono [&_code]:text-sm"
            // biome-ignore lint/security/noDangerouslySetInnerHtml: "this is needed."
            dangerouslySetInnerHTML={{ __html: html }}
          />
        )}
      </ViewerPanelShell>
    </CodeBlockContext.Provider>
  );
};

export type CodeBlockCopyButtonProps = ComponentProps<typeof Button> & {
  onCopy?: () => void;
  onError?: (error: Error) => void;
  timeout?: number;
};

export const CodeBlockCopyButton = ({
  onCopy,
  onError,
  timeout = 2000,
  children,
  className,
  ...props
}: CodeBlockCopyButtonProps) => {
  const [isCopied, setIsCopied] = useState(false);
  const { code } = useContext(CodeBlockContext);

  const copyToClipboard = async () => {
    // 剪贴板不可用时通过 onError 交给外层提示，不在组件内部伪造复制成功状态。
    if (typeof window === "undefined" || !navigator?.clipboard?.writeText) {
      onError?.(new Error("Clipboard API not available"));
      return;
    }

    try {
      // 只有写入成功才切换图标，并在短暂反馈后恢复复制按钮状态。
      await navigator.clipboard.writeText(code);
      setIsCopied(true);
      onCopy?.();
      setTimeout(() => setIsCopied(false), timeout);
    } catch (error) {
      onError?.(error as Error);
    }
  };

  const Icon = isCopied ? CheckIcon : CopyIcon;

  return (
    <Button
      className={cn(
        "h-7 w-7 shrink-0 rounded-md bg-[var(--chat-surface)] text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]",
        isCopied && "text-[var(--success)]",
        className
      )}
      onClick={copyToClipboard}
      size="icon-sm"
      variant="ghost"
      {...props}
    >
      {children ?? <Icon className="size-3.5" />}
    </Button>
  );
};
