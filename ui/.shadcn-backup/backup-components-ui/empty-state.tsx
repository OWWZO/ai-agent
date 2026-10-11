import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface EmptyStateProps {
  icon?: LucideIcon;
  title?: string;
  description?: string;
  action?: ReactNode;
  variant?: "default" | "compact";
  className?: string;
}

function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  variant = "default",
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center text-center",
        variant === "default" && "px-4 py-12",
        variant === "compact" && "px-4 py-6",
        className
      )}
    >
      {Icon && (
        <div
          className={cn(
            "flex items-center justify-center rounded-xl bg-[var(--primary)]/10 text-[var(--primary)]",
            variant === "default" && "mb-4 h-12 w-12",
            variant === "compact" && "mb-3 h-9 w-9"
          )}
        >
          <Icon
            className={cn(
              variant === "default" && "h-6 w-6",
              variant === "compact" && "h-[18px] w-[18px]"
            )}
          />
        </div>
      )}
      {title && (
        <h3
          className={cn(
            "font-semibold text-[var(--chat-text)]",
            variant === "default" && "text-[15px]",
            variant === "compact" && "text-[14px]"
          )}
        >
          {title}
        </h3>
      )}
      {description && (
        <p
          className={cn(
            "mt-1 text-[var(--chat-text-muted)]",
            variant === "default" && "text-[13px]",
            variant === "compact" && "text-[12px]"
          )}
        >
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export { EmptyState };
