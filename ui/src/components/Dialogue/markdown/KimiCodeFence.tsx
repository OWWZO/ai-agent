import {
  memo,
  useCallback,
  useEffect,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { CheckIcon, CopyIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { codeToTokens, type BundledLanguage, type ThemedToken } from "shiki";

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

async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
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

  const onCopy = useCallback(async () => {
    const ok = await copyText(code);
    if (!ok) return;
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1400);
  }, [code]);

  return (
    <div className={cn("kimi-code-fence", className)}>
      <div className="kimi-code-fence-bar">
        <span className="kimi-code-fence-lang">{lang}</span>
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

function tokenStyle(token: ThemedToken): CSSProperties | undefined {
  if (!token.color && !token.fontStyle) {
    return undefined;
  }
  const style: CSSProperties = {};
  if (token.color) {
    style.color = token.color;
  }
  // shiki FontStyle: italic=1, bold=2, underline=4
  const fontStyle = token.fontStyle ?? 0;
  if (fontStyle & 1) {
    style.fontStyle = "italic";
  }
  if (fontStyle & 2) {
    style.fontWeight = 700;
  }
  if (fontStyle & 4) {
    style.textDecoration = "underline";
  }
  return style;
}

async function tokenizeCode(
  code: string,
  language: BundledLanguage,
  dark: boolean
): Promise<ThemedToken[][]> {
  const result = await codeToTokens(code, {
    lang: language,
    theme: dark ? "github-dark" : "github-light",
  });
  return result.tokens;
}

export const KimiCodeFence = memo(function KimiCodeFence({
  code,
  language,
}: {
  code: string;
  language: BundledLanguage | string;
}) {
  const [lines, setLines] = useState<ThemedToken[][] | null>(null);
  const lang = (language || "text") as BundledLanguage;

  useEffect(() => {
    let alive = true;
    // 源码若已是 Shiki HTML，说明上游串台；只当纯文本展示，绝不二次注入
    if (/^\s*<pre\b[^>]*\bshiki\b/i.test(code)) {
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
  }, [code, lang]);

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
