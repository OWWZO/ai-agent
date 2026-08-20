import { useCallback, useRef, useState, type ReactNode } from "react";
import { ChevronRightIcon, ListIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { StatusDot } from "./StatusDot";

type AggregateStatus = "running" | "error" | "done";

function statusLabel(status: AggregateStatus): string {
  switch (status) {
    case "running":
      return "running";
    case "error":
      return "error";
    default:
      return "done";
  }
}

export function ToolGroup({
  count,
  aggregateStatus,
  defaultOpen = true,
  children,
}: {
  count: number;
  aggregateStatus: AggregateStatus;
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
        onClick={() => {
          setOpen((v) => !v);
          pinScroll();
        }}
      >
        <StatusDot status={aggregateStatus} />
        <ListIcon className="size-3.5 shrink-0 text-[var(--color-text-faint)]" />
        <span className="kimi-tool-group-title">
          {count} tool{count === 1 ? "" : "s"}
        </span>
        <span className="kimi-tool-group-meta">
          · {statusLabel(aggregateStatus)}
        </span>
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
