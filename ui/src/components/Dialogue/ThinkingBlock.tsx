import {
  memo,
  useEffect,
  useMemo,
  useRef,
  type MouseEventHandler,
} from "react";
import { cn } from "@/lib/utils";

type ThinkingBlockProps = {
  text: string;
  streaming?: boolean;
  foldable?: boolean;
  onOpen?: () => void;
  className?: string;
};

function splitParagraphs(text: string): string[] {
  return text
    .split(/\n{2,}/)
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
}

export const ThinkingBlock = memo(function ThinkingBlock({
  text,
  streaming = false,
  foldable = true,
  onOpen,
  className,
}: ThinkingBlockProps) {
  const bodyRef = useRef<HTMLPreElement | null>(null);
  const paragraphs = useMemo(() => splitParagraphs(text), [text]);
  const isFoldable = foldable && paragraphs.length > 1;
  const open = streaming || !isFoldable;
  const teaser = paragraphs[paragraphs.length - 1] ?? "";

  useEffect(() => {
    const el = bodyRef.current;
    if (!el || !streaming) return;
    el.scrollTop = el.scrollHeight;
  }, [streaming]);

  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
    if (!atBottom) return;
    el.scrollTop = el.scrollHeight;
  }, [text]);

  const handleOpen: MouseEventHandler = (event) => {
    event.preventDefault();
    onOpen?.();
  };

  if (!text.trim()) {
    return null;
  }

  if (!isFoldable) {
    return (
      <div className={cn("kimi-think", className)}>
        <pre ref={bodyRef} className="kimi-think-live">
          {text}
        </pre>
      </div>
    );
  }

  return (
    <div className={cn("kimi-think", className)}>
      <button
        type="button"
        className={cn("kimi-think-wrap", !open && "is-collapsed")}
        onClick={handleOpen}
        aria-label="查看完整思考"
      >
        <div className="kimi-think-anim">
          <pre ref={bodyRef} className="kimi-think-live">
            {text}
          </pre>
        </div>
        <div className="kimi-think-prev-anim">
          <span className="kimi-think-teaser">{teaser}</span>
        </div>
      </button>
    </div>
  );
});
