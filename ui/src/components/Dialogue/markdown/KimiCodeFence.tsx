import {
  memo,
  useCallback,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { CheckIcon, CopyIcon, WrapText } from "lucide-react";
import { useCodePreviewWrap } from "@/hooks/useCodePreviewWrap";
import { cn } from "@/lib/utils";
import type { BundledLanguage, ThemedToken } from "shiki";
import { tokenizeCode, tokenStyle } from "@/lib/shikiTokens";
import { shouldTokenizeCode } from "./codeHighlightPolicy";
import { copyToClipboard } from "@/utils";

type DiffRow = {
  type: "add" | "del" | "hunk" | "ctx";
  sign: string;
  text: string;
};

export function parseDiffLines(code: string): DiffRow[] {
  return code.split("\n").map((line) => {
    if (line.startsWith("@@")) return { type: "hunk", sign: "", text: line };
    if (/^\+(?!\+\+)/.test(line))
      return { type: "add", sign: "+", text: line.slice(1) };
    if (/^-(?!--)/.test(line))
      return { type: "del", sign: "-", text: line.slice(1) };
    if (line.startsWith(" "))
      return { type: "ctx", sign: "", text: line.slice(1) };
    return { type: "ctx", sign: "", text: line };
  });
}

/** @deprecated 保留给旧测试；fence 已改为 tokens 渲染，不再注入 HTML */
export function unwrapShikiPre(html: string): string {
  const trimmed = html.trim();
  const matched = trimmed.match(/^<pre\b[^>]*>([\s\S]*)<\/pre>$/i);
  return matched ? matched[1] : trimmed;
}

function FenceChrome({
  lang,
  code,
  children,
  className,
}: {
  lang: string;
  code: string;
  children: ReactNode;
  className?: string;
}) {
  const [copied, setCopied] = useState(false);
  const [wrap, , toggleWrap] = useCodePreviewWrap();

  const onCopy = useCallback(async () => {
    const ok = await copyToClipboard(code);
    if (!ok) return;
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1400);
  }, [code]);

  return (
    <div
      className={cn("kimi-code-fence", className)}
      data-wrap={wrap ? "true" : "false"}
    >
      <div className="kimi-code-fence-bar">
        <span className="kimi-code-fence-lang">{lang}</span>
        <button
          type="button"
          className="kimi-code-fence-wrap"
          aria-label="自动换行"
          aria-pressed={wrap}
          title={wrap ? "自动换行：已开启" : "自动换行：已关闭"}
          onClick={toggleWrap}
        >
          <WrapText className="size-3.5" />
        </button>
        <button
          type="button"
          className="kimi-code-fence-copy"
          aria-label="Copy code"
          onClick={onCopy}
        >
          {copied ? (
            <CheckIcon className="size-3.5" />
          ) : (
            <CopyIcon className="size-3.5" />
          )}
        </button>
      </div>
      {children}
    </div>
  );
}

export const DiffCodeFence = memo(function DiffCodeFence({
  code,
}: {
  code: string;
}) {
  const rows = parseDiffLines(code);
  return (
    <FenceChrome lang="diff" code={code} className="kimi-diff-wrap">
      <pre className="kimi-diff-pre">
        <code>
          {rows.map((ln, j) => (
            <span
              key={j}
              className={cn("kimi-diff-line", `kimi-diff-${ln.type}`)}
            >
              {ln.type !== "hunk" ? (
                <span className="kimi-diff-sign">{ln.sign}</span>
              ) : null}
              <span className="kimi-diff-text">{ln.text}</span>
            </span>
          ))}
        </code>
      </pre>
    </FenceChrome>
  );
});

export const KimiCodeFence = memo(function KimiCodeFence({
  code,
  language,
  streaming = false,
}: {
  code: string;
  language: BundledLanguage | string;
  /**
   * 流式进行中：**跳过 shiki 高亮**，直接渲染纯文本。
   *
   * 对齐 ZCode（`message.tsx` 的 `enableSyntaxHighlighting={!renderStreaming}`）：
   * 流式时 `code` 每帧都在变，tokenize 是异步的，会和消息流更新叠加 ——
   * 既浪费 CPU（每个 token 重跑一遍 TextMate 语法），又容易在高频更新下产生抖动。
   * 消息落定后 `streaming` 转 false，再一次性高亮。
   */
  streaming?: boolean;
}) {
  const [lines, setLines] = useState<ThemedToken[][] | null>(null);
  const lang = (language || "text") as BundledLanguage;

  useEffect(() => {
    let alive = true;
    // 流式期间不做高亮：等落定后 effect 会因 streaming 变化重新执行。
    if (!shouldTokenizeCode(code, streaming)) {
      setLines(null);
      return () => {
        alive = false;
      };
    }
    const preferDark = document.documentElement.classList.contains("dark");
    tokenizeCode(code, lang, preferDark)
      .then((tokens) => {
        if (alive) setLines(tokens);
      })
      .catch(() => {
        if (alive) setLines(null);
      });
    return () => {
      alive = false;
    };
  }, [code, lang, streaming]);

  return (
    <FenceChrome lang={String(language || "text")} code={code}>
      {lines ? (
        <pre className="kimi-code-fence-body">
          <code>
            {lines.map((line, lineIdx) => (
              <span key={lineIdx} className="kimi-code-line">
                {line.map((token, tokenIdx) => (
                  <span key={tokenIdx} style={tokenStyle(token)}>
                    {token.content}
                  </span>
                ))}
                {lineIdx < lines.length - 1 ? "\n" : null}
              </span>
            ))}
          </code>
        </pre>
      ) : (
        <pre className="kimi-code-fence-fallback">
          <code>{code}</code>
        </pre>
      )}
    </FenceChrome>
  );
});
