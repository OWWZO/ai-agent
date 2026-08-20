import type { CSSProperties } from "react";
import { cn } from "@/lib/utils";

const MOON_FRAMES = ["🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘"] as const;
const MOON_FRAME_MS = 120;
const MOON_FAST_FRAME_MS = 60;

type MoonSpinnerProps = {
  size?: "sm" | "md" | "lg";
  fast?: boolean;
  label?: string;
  className?: string;
};

export function MoonSpinner({
  size = "md",
  fast = false,
  label = "Waiting for response…",
  className,
}: MoonSpinnerProps) {
  return (
    <span
      className={cn(
        "ui-moon",
        `ui-moon--${size}`,
        fast && "ui-moon--fast",
        className
      )}
      aria-label={label}
      role="img"
    >
      {MOON_FRAMES.map((frame, index) => (
        <span
          key={frame}
          className="ui-moon__frame"
          style={
            {
              "--moon-frame-delay": `${index * MOON_FRAME_MS}ms`,
              "--moon-frame-fast-delay": `${index * MOON_FAST_FRAME_MS}ms`,
            } as CSSProperties
          }
          aria-hidden
        >
          {frame}
        </span>
      ))}
    </span>
  );
}
