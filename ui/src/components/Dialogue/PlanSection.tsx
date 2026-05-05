import { FC, memo, useMemo } from "react";
import { motion } from "motion/react";
import { Layers } from "lucide-react";

export type PlanSectionProps = {
  plan?: CHAT.Plan;
  versionLabel?: string;
  onPrev?: () => void;
  onNext?: () => void;
  canPrev?: boolean;
  canNext?: boolean;
  staticSnapshot?: boolean;
};

const normalizePlanForDisplay = (plan?: CHAT.Plan) => {
  if (!plan) {
    return null;
  }

  const steps = Array.isArray(plan.steps) ? plan.steps : [];
  const stages =
    Array.isArray(plan.stages) && plan.stages.length ? plan.stages : steps;
  const stepStatus =
    Array.isArray(plan.stepStatus) && plan.stepStatus.length
      ? plan.stepStatus
      : Array.from({ length: stages.length }, () => "completed");

  return {
    title: plan.title || "执行计划",
    stages,
    steps,
    stepStatus,
  };
};

const resolvePlanStepDetail = (
  plan: ReturnType<typeof normalizePlanForDisplay>,
  index: number
) => {
  if (!plan) {
    return "";
  }

  if (
    Array.isArray(plan.stages) &&
    Array.isArray(plan.steps) &&
    plan.stages[index] === plan.steps[index]
  ) {
    return "";
  }

  return plan.steps[index] || "";
};

const resolvePlanStepTone = (status?: string) => {
  switch (status) {
    case "completed":
      return {
        badgeClass: "bg-[#0071e3]/10 text-[#0071e3]",
        dotClass: "bg-[#0071e3]",
        label: "已完成",
      };
    case "in_progress":
      return {
        badgeClass: "bg-amber-500/10 text-amber-600",
        dotClass: "bg-amber-500",
        label: "进行中",
      };
    default:
      return {
        badgeClass: "bg-[var(--chat-surface-muted)] text-[var(--chat-text-muted)]",
        dotClass: "bg-[var(--chat-text-muted)]",
        label: "未开始",
      };
  }
};

export const PlanSection: FC<PlanSectionProps> = memo(({
  plan,
  versionLabel,
  onPrev,
  onNext,
  canPrev,
  canNext,
  staticSnapshot = false,
}) => {
  const normalizedPlan = useMemo(() => normalizePlanForDisplay(plan), [plan]);

  if (!normalizedPlan || !normalizedPlan.stages.length) {
    return null;
  }

  const completedCount = normalizedPlan.stepStatus.filter(
    (status) => status === "completed"
  ).length;

  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 10
      }}
      animate={{
        opacity: 1,
        y: 0
      }}
      transition={{
        duration: 0.24,
        ease: [0.25, 0.46, 0.45, 0.94]
      }}
      className="overflow-hidden rounded-2xl bg-[var(--chat-surface-soft)]/90 px-4 py-4 shadow-[var(--shadow-sm)] ring-0"
    >
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-surface)]/95 text-[var(--chat-text-soft)] shadow-[var(--shadow-xs)]">
            <Layers className="h-5 w-5" strokeWidth={1.75} />
          </div>
          <div className="min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--chat-text-muted)]">
              研究路线
            </p>
            <p
              className="text-[15px] font-semibold leading-snug tracking-[-0.02em] text-[var(--chat-text)]"
              style={{ fontFamily: "var(--font-sans)" }}
            >
              {normalizedPlan.title}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {versionLabel ? (
            <div className="inline-flex items-center gap-1 rounded-full bg-[var(--chat-surface)] px-2 py-1 text-[11px] font-medium text-[var(--chat-text-soft)]">
              <button
                type="button"
                className="rounded px-1 disabled:opacity-40"
                onClick={onPrev}
                disabled={!canPrev}
              >
                {"<"}
              </button>
              <span>{versionLabel}</span>
              <button
                type="button"
                className="rounded px-1 disabled:opacity-40"
                onClick={onNext}
                disabled={!canNext}
              >
                {">"}
              </button>
            </div>
          ) : null}
          {!staticSnapshot ? (
            <div className="shrink-0 rounded-full bg-[var(--chat-surface)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
              {completedCount}/{normalizedPlan.stages.length}
            </div>
          ) : (
            <div className="shrink-0 rounded-full bg-[var(--chat-surface)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
              历史快照
            </div>
          )}
        </div>
      </div>
      <div className="space-y-2.5">
        {normalizedPlan.stages.map((stage, index) => {
          const status = normalizedPlan.stepStatus[index];
          const tone = resolvePlanStepTone(status);
          const stepDetail = resolvePlanStepDetail(normalizedPlan, index);

          return (
            <motion.div
              key={`${stage}-${index}`}
              initial={{
                opacity: 0,
                x: -6
              }}
              animate={{
                opacity: 1,
                x: 0
              }}
              transition={{
                delay: Math.min(index * 0.06, 0.36),
                duration: 0.22,
                ease: [0.25, 0.46, 0.45, 0.94],
              }}
              className="rounded-xl bg-[var(--chat-surface)]/75 px-3 py-3 shadow-[var(--shadow-xs)]"
            >
              <div className="flex items-start gap-3">
                <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[var(--chat-surface-muted)] text-[12px] font-semibold tabular-nums text-[var(--chat-text-soft)]">
                  {index + 1}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-[14px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]">
                      {stage}
                    </span>
                    {!staticSnapshot ? (
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${tone.badgeClass}`}
                      >
                        <span className={`h-1.5 w-1.5 rounded-full ${tone.dotClass}`}></span>
                        {tone.label}
                      </span>
                    ) : null}
                  </div>
                  {stepDetail ? (
                    <p className="mt-2 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
                      {stepDetail}
                    </p>
                  ) : null}
                </div>
              </div>
            </motion.div>
          );
        })}
      </div>
    </motion.div>
  );
});

PlanSection.displayName = "PlanSection";
