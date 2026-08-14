import type { CSSProperties } from "react";

/** Fluid stage: fill width, grow with viewport, only lock height when model asks. */

export const STAGE_CLASS =
  "w-full min-h-[260px] max-h-[min(56vh,640px)] aspect-[16/10]";

export const STAGE_TALL_CLASS =
  "w-full min-h-[280px] max-h-[min(64vh,720px)] aspect-[4/3]";

export function stageStyle(height?: number | string): CSSProperties | undefined {
  const n = typeof height === "number" ? height : Number(height);
  if (Number.isFinite(n) && n > 0) {
    return { minHeight: Math.max(200, n), height: n, maxHeight: "none", aspectRatio: "auto" };
  }
  return undefined;
}
