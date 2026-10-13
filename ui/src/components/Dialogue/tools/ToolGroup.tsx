import { useCallback, useRef, useState, type ReactNode } from "react";
import { ChevronRightIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export function ToolGroup({
  title,
  count,
  defaultOpen = true,
  children,
}: {
  /**
   * 组标题。**必须由 `agentProcessModel.buildGroupTitle()` 统一生成**
   * （「执行了 N 个步骤」/「正在执行 · N 个步骤」），不要在这里另拼文案 ——
   * 曾经这里硬编码「N 个工具 · 已完成」，导致纯工具组和混合组出现两套 UI。
   */
  title: string;
  /** 仅用于无障碍标签。 */
  count: number;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const headRef = useRef<HTMLButtonElement | null>(null);

  const pinScroll = useCallback(() => {
    const el = headRef.current;
    if (!el) return;
    const top = el.getBoundingClientRect().top;
    requestAnimationFrame(() => {
      const next = el.getBoundingClientRect().top;
      const delta = next - top;
      if (Math.abs(delta) > 1) {
        const scroller = el.closest(
          "[data-stick-to-bottom], .overflow-y-auto, .overflow-auto"
        );
        if (scroller instanceof HTMLElement) {
          scroller.scrollTop += delta;
        }
      }
    });
  }, []);

  return (
    <div className={cn("kimi-tool-group", open && "is-open")}>
      <button
        ref={headRef}
        type="button"
        className="kimi-tool-group-head"
        aria-expanded={open}
        aria-label={`${title}（${count} 个工具）`}
        onClick={() => {
          setOpen((v) => !v);
          pinScroll();
        }}
      >
        <span className="kimi-tool-group-title">{title}</span>
        <ChevronRightIcon className="kimi-tool-group-chevron size-3.5" />
      </button>
      <div
        className={cn("kimi-tool-group-body", open && "is-open")}
        inert={!open ? true : undefined}
      >
        <div className="kimi-tool-group-body-inner">{children}</div>
      </div>
    </div>
  );
}
