import { FC, memo, useRef, useState } from "react";

function isImageSrc(v?: string): boolean {
  if (!v) return false;
  return /^(https?:|data:image|\/|\.\/)/i.test(v) || /\.(png|jpe?g|gif|webp|svg)(\?|$)/i.test(v);
}

export const BeforeAfter: FC<{
  before?: string;
  after?: string;
  beforeLabel?: string;
  afterLabel?: string;
  height?: number;
}> = memo(({ before, after, beforeLabel = "之前", afterLabel = "之后", height }) => {
  const [pos, setPos] = useState(50);
  const track = useRef<HTMLDivElement | null>(null);
  const left = String(before || "");
  const right = String(after || "");
  const img = isImageSrc(left) && isImageSrc(right);

  const move = (clientX: number) => {
    const el = track.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const p = ((clientX - rect.left) / rect.width) * 100;
    setPos(Math.min(96, Math.max(4, p)));
  };

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)]">
      <div
        ref={track}
        className="relative select-none overflow-hidden bg-[var(--chat-surface-soft)]"
        style={{ minHeight: height || 240, aspectRatio: height ? undefined : "16 / 10" }}
        onPointerDown={(e) => {
          (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
          move(e.clientX);
        }}
        onPointerMove={(e) => {
          if (e.buttons) move(e.clientX);
        }}
      >
        <div className="absolute inset-0">
          {img ? (
            <img src={right} alt={afterLabel} className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full items-center justify-center p-6 text-[14px] leading-relaxed text-[var(--chat-text)]">
              {right || afterLabel}
            </div>
          )}
        </div>
        <div className="absolute inset-0 overflow-hidden" style={{ width: `${pos}%` }}>
          {img ? (
            <img
              src={left}
              alt={beforeLabel}
              className="h-full max-w-none object-cover"
              style={{ width: `${10000 / pos}%` }}
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center bg-[var(--chat-surface)] p-6 text-[14px] leading-relaxed text-[var(--chat-text)]">
              {left || beforeLabel}
            </div>
          )}
        </div>
        <div
          className="absolute inset-y-0 z-[1] w-0.5 bg-white shadow"
          style={{ left: `${pos}%` }}
        >
          <div className="absolute top-1/2 left-1/2 flex h-8 w-8 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] text-[11px] text-[var(--chat-text-soft)] shadow">
            ↔
          </div>
        </div>
      </div>
      <div className="flex justify-between px-3 py-1.5 text-[11px] text-[var(--chat-text-soft)]">
        <span>{beforeLabel}</span>
        <span>{afterLabel}</span>
      </div>
    </div>
  );
});

BeforeAfter.displayName = "BeforeAfter";
