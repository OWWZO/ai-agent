import { cn } from "@/lib/utils";
import type { HTMLAttributes, ReactNode } from "react";

export type ViewerPanelShellProps = {
  label: string;
  subtitle?: string;
  headerRight?: ReactNode;
  children: ReactNode;
  /** 下方内容区（浅色底）额外 class，例如 `p-0` 让 iframe 贴边 */
  bodyClassName?: string;
} & Omit<HTMLAttributes<HTMLDivElement>, "children">;

export function ViewerPanelShell({
  label,
  subtitle,
  headerRight,
  children,
  className,
  bodyClassName,
  ...props
}: ViewerPanelShellProps) {
  return (
    <div
      className={cn(
        "relative w-full overflow-hidden rounded-xl bg-[var(--chat-surface)] shadow-[var(--shadow-md)]",
        className
      )}
      {...props}
    >
      <div className="flex items-center justify-between gap-3 bg-[var(--chat-surface-soft)]/75 px-3 py-2">
        <div className="flex min-w-0 items-center gap-2">
          <span className="inline-flex items-center rounded-md bg-[var(--chat-surface)] px-2 py-0.5 font-mono text-[11px] font-semibold uppercase tracking-[0.14em] text-[var(--chat-text-soft)] shadow-[var(--shadow-xs)]">
            {label}
          </span>
          {subtitle ? (
            <span className="hidden truncate text-[12px] text-[var(--chat-text-muted)] sm:inline">
              {subtitle}
            </span>
          ) : null}
        </div>
        {headerRight ? (
          <div className="flex shrink-0 items-center gap-1.5">{headerRight}</div>
        ) : null}
      </div>
      <div
        className={cn(
          "relative bg-[var(--json-view-inner-bg)] px-3 py-3 sm:px-4 sm:py-4",
          bodyClassName
        )}
      >
        {children}
      </div>
    </div>
  );
}
