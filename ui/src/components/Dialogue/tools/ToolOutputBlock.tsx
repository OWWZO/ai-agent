import type { CSSProperties } from "react";
import { cn } from "@/lib/utils";

const OUTPUT_SCROLL_LINE_COUNT = 50;

export function ToolOutputBlock({
  lines,
  emptyText = "Waiting for output…",
  tone = "default",
}: {
  lines?: string[];
  emptyText?: string;
  tone?: "default" | "error";
}) {
  const outputLines = lines ?? [];
  const isScrollable = outputLines.length > OUTPUT_SCROLL_LINE_COUNT;

  return (
    <div
      className={cn(
        "kimi-tool-output",
        isScrollable && "is-scroll",
        tone === "error" && "is-error"
      )}
      style={
        {
          "--tool-output-visible-lines": String(OUTPUT_SCROLL_LINE_COUNT),
        } as CSSProperties
      }
    >
      {outputLines.length === 0 ? (
        <div className="kimi-tool-output-empty">{emptyText}</div>
      ) : (
        outputLines.map((line, i) => <div key={i}>{line}</div>)
      )}
    </div>
  );
}
