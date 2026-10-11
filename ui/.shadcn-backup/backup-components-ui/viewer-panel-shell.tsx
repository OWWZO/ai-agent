import { cn } from "@/lib/utils";
import type { HTMLAttributes, ReactNode } from "react";

export type ViewerPanelShellProps = {
  label: string;
  subtitle?: string;
  headerRight?: ReactNode;
  children: ReactNode;
  /** 隐藏顶栏（工作区已有外层文件头时避免重复） */
  hideHeader?: boolean;
  /** 下方内容区（浅色底）额外 class，例如 `p-0` 让 iframe 贴边 */
  bodyClassName?: string;
} & Omit<HTMLAttributes<HTMLDivElement>, "children">;

export function ViewerPanelShell({
  label,
  subtitle,
  headerRight,
  children,
  className,
  hideHeader = false,
  bodyClassName,
  ...props
}: ViewerPanelShellProps) {
  return (
    <div
      className={cn(
        "relative flex w-full min-h-0 flex-col overflow-hidden rounded-xl bg-[var(--chat-surface)] shadow-[var(--shadow-md)]",
        hideHeader && "rounded-none shadow-none",
        className
      )}
      {...props}
    >
      {hideHeader ? null : (
        <div className="flex shrink-0 items-center justify-between gap-3 border-b border-[var(--chat-border)]/40 bg-[var(--chat-surface)] px-3 py-2">
          <div className="flex min-w-0 items-center gap-2">
            <span className="inline-flex items-center rounded-md bg-[var(--chat-surface-muted)] px-2 py-0.5 font-mono text-[11px] font-semibold uppercase tracking-[0.14em] text-[var(--chat-text-soft)]">
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
      )}
      <div
        className={cn(
          "relative min-h-0 flex-1 bg-[var(--chat-surface)] px-3 py-3 sm:px-4 sm:py-4",
          bodyClassName
        )}
      >
        {children}
      </div>
    </div>
  );
}
