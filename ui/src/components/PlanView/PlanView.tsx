import { forwardRef, useImperativeHandle, useMemo, useState } from "react";
import { motion } from "motion/react";
import { ListOrdered } from "lucide-react";

import {
  Plan,
  PlanHeader,
  PlanTitle,
  PlanTrigger,
  PlanContent,
} from "@/components/ai-elements/plan";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

import { getStatusIcon } from "./config";

export type PlanViewAction = {
  closePlanView: () => void;
  openPlanView: () => void;
  togglePlanView: () => void;
};

const PlanView: ReactorType.FC<{
  plan?: CHAT.Plan;
  ref?: React.Ref<PlanViewAction>;
}> = forwardRef((props, ref) => {
  const { plan } = props;
  const { stages, stepStatus, steps } = plan || {};

  const [open, setOpen] = useState(false);

  useImperativeHandle(ref, () => ({
    openPlanView: () => setOpen(true),
    closePlanView: () => setOpen(false),
    togglePlanView: () => setOpen((v) => !v),
  }));

  const total = stages?.length ?? 0;
  const completed = useMemo(
    () => stepStatus?.filter((s) => s === "completed").length ?? 0,
    [stepStatus]
  );
  const pct = total > 0 ? Math.min(100, Math.round((completed / total) * 100)) : 0;

  const isStreaming = Boolean(
    plan && stepStatus && stepStatus.length > 0 && !stepStatus.every((s) => s === "completed")
  );

  if (!plan) {
    return null;
  }

  return (
    <div className="w-full px-3 pb-1">
      <Plan open={open} onOpenChange={setOpen} isStreaming={isStreaming}>
        <PlanHeader>
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <motion.div
              initial={false}
              animate={
                isStreaming
                  ? {
                    scale: [1, 1.04, 1],
                    opacity: [0.85, 1, 0.85],
                  }
                  : {
                    scale: 1,
                    opacity: 1,
                  }
              }
              transition={
                isStreaming
                  ? {
                    duration: 2.2,
                    repeat: Number.POSITIVE_INFINITY,
                    ease: "easeInOut",
                  }
                  : { duration: 0.2 }
              }
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-surface)]/95 text-[var(--chat-text-soft)] shadow-[var(--shadow-xs)]"
            >
              <ListOrdered className="h-5 w-5" strokeWidth={1.75} />
            </motion.div>
            <div className="min-w-0 flex-1">
              <PlanTitle>任务进度</PlanTitle>
              <p className="mt-0.5 text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--chat-text-muted)]">
                深度研究
              </p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            {total > 0 ? (
              <Badge variant="secondary" className="h-6 px-2.5 text-[11px] font-medium tabular-nums">
                {completed}/{total}
              </Badge>
            ) : null}
            <PlanTrigger />
          </div>
        </PlanHeader>

        <PlanContent>
          {total > 0 ? (
            <div className="mb-1">
              <div className="mb-1.5 flex items-center justify-between text-[11px] text-[var(--chat-text-muted)]">
                <span className="font-medium uppercase tracking-[0.06em]">整体进度</span>
                <span className="tabular-nums text-[var(--chat-text-soft)]">{pct}%</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-[var(--chat-surface-muted)]">
                <motion.div
                  className="h-full rounded-full bg-[#0071e3]/90"
                  initial={false}
                  animate={{ width: `${pct}%` }}
                  transition={{
                    type: "spring",
                    stiffness: 380,
                    damping: 32,
                  }}
                />
              </div>
            </div>
          ) : null}

          <div className="space-y-2 pt-1">
            {stages?.map((name, index) => {
              const status = stepStatus?.[index];
              const active = status === "in_progress";
              const done = status === "completed";

              return (
                <motion.div
                  key={`${name}-${index}`}
                  layout
                  initial={{
                    opacity: 0,
                    y: 8,
                  }}
                  animate={{
                    opacity: 1,
                    y: 0,
                  }}
                  transition={{
                    delay: Math.min(index * 0.05, 0.35),
                    duration: 0.22,
                    ease: [0.25, 0.46, 0.45, 0.94],
                  }}
                  className={cn(
                    "flex gap-1 rounded-xl px-2 py-2.5 transition-[background-color,box-shadow] duration-200 md:gap-2 md:px-3",
                    active &&
                      "bg-[var(--chat-surface)]/90 shadow-[var(--shadow-xs)] ring-0",
                    done && !active && "opacity-[0.92]",
                    status === "not_started" && "bg-transparent"
                  )}
                >
                  <div className="flex w-8 shrink-0 justify-center">{getStatusIcon(status)}</div>
                  <div className="min-w-0 flex-1 pt-0.5">
                    <div className="text-[14px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]">
                      {name}
                    </div>
                    {steps?.[index] ? (
                      <p className="mt-1 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
                        {steps[index]}
                      </p>
                    ) : null}
                  </div>
                </motion.div>
              );
            })}
          </div>
        </PlanContent>
      </Plan>
    </div>
  );
});

PlanView.displayName = "PlanView";

export default PlanView;
