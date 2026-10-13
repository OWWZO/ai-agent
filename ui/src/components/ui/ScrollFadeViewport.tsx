import { useEffect, useRef, useState, type ComponentProps, type ReactNode } from "react";
import { cn } from "@/lib/utils";

type ScrollMaskState = "none" | "top" | "bottom" | "both";

/** 渐隐带的高度（px），对应 ZCode 的 `black_24px`。 */
const FADE_SIZE = 24;

/**
 * 滚动渐隐视口（对齐 ZCode `components/ui/scroll-fade-viewport.tsx`）。
 *
 * 按 `scrollTop` 位置给滚动容器加 `mask-image` 渐隐：只有对应方向**确实还有**
 * 被遮住的内容时才出现，滚到底就自动消失，不会在内容不满一屏时留一条假渐隐。
 *
 * 与 ZCode 的两点差异（都是为了不退化）：
 * 1. 滚动节点通过 `scrollRef` 显式回传，而不是 `useImperativeHandle` + `ref` ——
 *    调用方（`ExecuteOutput`）的吸底逻辑必须拿到**同一个** DOM 节点，显式传引用更直白。
 * 2. effect 依赖为空数组 + `ResizeObserver`。ZCode 依赖 `[children]`，而流式输出每帧
 *    都会产生新的 children，那会导致每帧重建一次 ResizeObserver。
 */
export function ScrollFadeViewport({
  scrollRef,
  className,
  children,
  ...props
}: {
  /** 把内部滚动节点回传给调用方（吸底 / 冻结逻辑需要直接操作它） */
  scrollRef?: { current: HTMLDivElement | null };
  className?: string;
  children?: ReactNode;
} & Omit<ComponentProps<"div">, "ref" | "children">) {
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [maskState, setMaskState] = useState<ScrollMaskState>("none");

  useEffect(() => {
    const viewport = viewportRef.current;
    const content = contentRef.current;
    if (!viewport || !content) {
      setMaskState("none");
      return;
    }

    const updateMaskState = () => {
      const maxScrollTop = viewport.scrollHeight - viewport.clientHeight;
      if (maxScrollTop <= 1) {
        setMaskState("none");
        return;
      }

      const hasHiddenTop = viewport.scrollTop > 1;
      const hasHiddenBottom = viewport.scrollTop < maxScrollTop - 1;
      setMaskState(
        hasHiddenTop && hasHiddenBottom
          ? "both"
          : hasHiddenTop
            ? "top"
            : hasHiddenBottom
              ? "bottom"
              : "none"
      );
    };

    let rafId: number | null = null;
    const scheduleUpdate = () => {
      if (rafId !== null) return;
      rafId = requestAnimationFrame(() => {
        rafId = null;
        updateMaskState();
      });
    };

    updateMaskState();
    viewport.addEventListener("scroll", updateMaskState, { passive: true });
    window.addEventListener("resize", scheduleUpdate);

    const resizeObserver =
      typeof ResizeObserver === "undefined" ? null : new ResizeObserver(scheduleUpdate);
    resizeObserver?.observe(viewport);
    resizeObserver?.observe(content);

    return () => {
      viewport.removeEventListener("scroll", updateMaskState);
      resizeObserver?.disconnect();
      window.removeEventListener("resize", scheduleUpdate);
      if (rafId !== null) cancelAnimationFrame(rafId);
    };
  }, []);

  return (
    <div
      ref={(node) => {
        viewportRef.current = node;
        if (scrollRef) scrollRef.current = node;
      }}
      data-scroll-mask={maskState}
      style={{ ["--scroll-fade-size" as string]: `${FADE_SIZE}px` }}
      className={cn("min-h-0 flex-1 overflow-y-auto", className)}
      {...props}
    >
      <div ref={contentRef}>{children}</div>
    </div>
  );
}
