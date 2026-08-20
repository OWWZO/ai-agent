import {
  useCallback,
  useRef,
  type ReactNode,
} from "react";
import { CheckIcon, ChevronDownIcon, ChevronRightIcon, XIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { StatusDot } from "./StatusDot";

export type ToolRowStackPosition = "single" | "first" | "middle" | "last";

type ToolRowProps = {
  status: "running" | "ok" | "error" | "suspended";
  icon?: ReactNode;
  name: string;
  arg?: string;
  time?: string;
  chip?: string;
  open?: boolean;
  expandable?: boolean;
  stacked?: boolean;
  stackPosition?: ToolRowStackPosition;
  trailing?: ReactNode;
  children?: ReactNode;
  onToggle?: () => void;
  onOpenWorkspace?: () => void;
};

export function ToolRow({
  status,
  icon,
  name,
  arg = "",
  time = "",
  chip = "",
  open = false,
  expandable = false,
  stacked = false,
  stackPosition = "single",
  trailing,
  children,
  onToggle,
  onOpenWorkspace,
}: ToolRowProps) {
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
        } else {
          window.scrollBy(0, delta);
        }
      }
    });
  }, []);

  const handleClick = () => {
    if (expandable) {
      onToggle?.();
      pinScroll();
      return;
    }
    onOpenWorkspace?.();
  };

  return (
    <div
      className={cn(
        "kimi-tool-row",
        open && "is-open",
        stacked && "is-stacked",
        status === "error" && "is-error",
        stackPosition === "first" && "is-stack-first",
        stackPosition === "middle" && "is-stack-middle",
        stackPosition === "last" && "is-stack-last"
      )}
    >
      <button
        ref={headRef}
        type="button"
        className="kimi-tool-row-head"
        aria-expanded={expandable ? open : undefined}
        onClick={handleClick}
      >
        {icon ? <span className="kimi-tool-row-glyph">{icon}</span> : null}
        <span className="kimi-tool-row-text">
          <span className="kimi-tool-row-name">{name}</span>
          {arg ? (
            <span className="kimi-tool-row-arg" title={arg}>
              {arg}
            </span>
          ) : null}
        </span>
        <span className="kimi-tool-row-trailing">
          <span
            className={cn(
              "kimi-tool-row-status",
              status === "ok" && "is-ok",
              status === "error" && "is-error"
            )}
            role="status"
            aria-label={status}
          >
            {status === "ok" ? (
              <CheckIcon className="size-3.5" />
            ) : status === "error" ? (
              <XIcon className="size-3.5" />
            ) : (
              <StatusDot status={status} />
            )}
          </span>
          {chip ? <span className="kimi-tool-row-chip">{chip}</span> : null}
          {trailing}
          {time ? <span className="kimi-tool-row-time">{time}</span> : null}
        </span>
        {expandable ? (
          open ? (
            <ChevronDownIcon className="size-3.5 shrink-0 text-[var(--color-text-faint)]" />
          ) : (
            <ChevronRightIcon className="size-3.5 shrink-0 text-[var(--color-text-faint)]" />
          )
        ) : null}
      </button>
      <div
        className={cn("kimi-tool-row-body", open && "is-open")}
        inert={!open ? true : undefined}
      >
        <div className="kimi-tool-row-body-pad">{children}</div>
      </div>
    </div>
  );
}
