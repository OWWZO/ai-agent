import { CheckCircle2, Circle, Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";

const iconWrap = "flex h-8 w-8 shrink-0 items-center justify-center";

export const getStatusIcon = (status?: CHAT.PlanStatus) => {
  switch (status) {
    case "not_started":
      return (
        <div className={iconWrap} aria-hidden>
          <Circle
            className="h-4 w-4 text-[var(--chat-text-muted)] opacity-55"
            strokeWidth={1.75}
          />
        </div>
      );
    case "in_progress":
      return (
        <div className={iconWrap} aria-hidden>
          <Loader2
            className="h-4 w-4 animate-spin text-[#0071e3]"
            strokeWidth={2}
          />
        </div>
      );
    case "completed":
      return (
        <div className={iconWrap} aria-hidden>
          <CheckCircle2
            className={cn("h-4 w-4 text-[var(--success)]")}
            strokeWidth={2}
          />
        </div>
      );
    default:
      return <div className={iconWrap} />;
  }
};