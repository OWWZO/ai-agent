import { FC, memo, useEffect, useMemo, useState } from "react";
import {
  ChevronDownIcon,
  ClipboardListIcon,
  LoaderCircleIcon,
} from "lucide-react";
import { message } from "antd";
import { planApprovalApi } from "@/services/planApproval";
import {
  buildComposerPlanModel,
  type ComposerPlanModel,
} from "./planComposerModel";

type PlanComposerBarProps = {
  chat?: CHAT.ChatItem;
  taskList?: CHAT.Task[];
  structuredPlan?: CHAT.Plan;
  loading?: boolean;
};

/**
 * 依附在输入框上方的计划条（Plan Mode / ExitPlanMode / PlanSolve stages）。
 */
const PlanComposerBar: FC<PlanComposerBarProps> = memo(
  ({ chat, taskList, structuredPlan, loading }) => {
    const model = useMemo(
      () => buildComposerPlanModel({ chat, taskList, structuredPlan }),
      [chat, taskList, structuredPlan]
    );

    if (!model) {
      return null;
    }

    return <PlanComposerBarInner model={model} loading={loading} />;
  }
);

const PlanComposerBarInner: FC<{ model: ComposerPlanModel; loading?: boolean }> = memo(
  ({ model, loading }) => {
    const [open, setOpen] = useState(true);
    const [feedback, setFeedback] = useState("");
    const [editedPlan, setEditedPlan] = useState(model.planContent);
    const [submitting, setSubmitting] = useState(false);
    const [localStatus, setLocalStatus] = useState(model.status);

    useEffect(() => {
      setEditedPlan(model.planContent);
      setLocalStatus(model.status);
      setFeedback("");
      // 有新计划正文时默认展开
      if (model.planContent.trim() && model.source === "plan_approval") {
        setOpen(true);
      }
    }, [model.approvalId, model.planContent, model.source, model.status]);

    const status = localStatus || model.status || "pending";
    const isDecided = status === "approved" || status === "rejected";
    const canApprove =
      model.source === "plan_approval" &&
      Boolean(model.approvalId) &&
      status === "pending" &&
      Boolean((editedPlan || model.planContent).trim());
    const showActions =
      model.source === "plan_approval" &&
      status === "pending" &&
      Boolean(model.approvalId) &&
      !isDecided;

    const statusLabel =
      status === "approved"
        ? "已批准 · 只读"
        : status === "rejected"
          ? "已拒绝 · 只读"
          : status === "planning"
            ? "规划中"
            : model.source === "plan_approval"
              ? "待批准"
              : "进行中";

    const approve = async () => {
      if (!model.approvalId || submitting || !canApprove) {
        return;
      }
      setSubmitting(true);
      try {
        const res = await planApprovalApi.approve({
          approvalId: model.approvalId,
          editedPlanContent:
            editedPlan !== model.planContent ? editedPlan : undefined,
          feedback: feedback.trim() || undefined,
        });
        if (res && (res as { accepted?: boolean }).accepted === false) {
          message.warning(
            String((res as { message?: string }).message || "批准失败")
          );
          return;
        }
        setLocalStatus("approved");
        message.success("计划已批准，Agent 将开始实现");
      } catch (error) {
        message.error(error instanceof Error ? error.message : "批准失败");
      } finally {
        setSubmitting(false);
      }
    };

    const reject = async () => {
      if (!model.approvalId || submitting || status !== "pending") {
        return;
      }
      setSubmitting(true);
      try {
        const res = await planApprovalApi.reject({
          approvalId: model.approvalId,
          feedback: feedback.trim() || "需要修订计划",
        });
        if (res && (res as { accepted?: boolean }).accepted === false) {
          message.warning(
            String((res as { message?: string }).message || "拒绝失败")
          );
          return;
        }
        setLocalStatus("rejected");
        message.success("已拒绝，Agent 将继续修订计划");
      } catch (error) {
        message.error(error instanceof Error ? error.message : "拒绝失败");
      } finally {
        setSubmitting(false);
      }
    };

    return (
      <div className="mb-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/45 bg-[var(--chat-surface)]/95 shadow-[var(--shadow-sm)]">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="flex w-full items-center gap-2.5 px-3 py-2.5 text-left transition-colors hover:bg-[var(--chat-surface-soft)]/60"
        >
          <div className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-accent)]/12 text-[var(--chat-accent)]">
            {loading && status === "planning" ? (
              <LoaderCircleIcon className="size-4 animate-spin" />
            ) : (
              <ClipboardListIcon className="size-4" />
            )}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="truncate text-[13px] font-semibold text-[var(--chat-text)]">
                {model.title}
              </span>
              <span className="shrink-0 rounded-full bg-[var(--chat-surface-muted)] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-soft)]">
                {statusLabel}
              </span>
            </div>
            <div className="mt-0.5 line-clamp-1 text-[11px] text-[var(--chat-text-soft)]">
              {model.planContent.replace(/\s+/g, " ").slice(0, 80) || "暂无正文"}
            </div>
          </div>
          <ChevronDownIcon
            className={[
              "size-4 shrink-0 text-[var(--chat-text-soft)] transition-transform",
              open ? "rotate-180" : "",
            ].join(" ")}
          />
        </button>

        {open ? (
          <div className="border-t border-[var(--chat-border)]/30 px-3 pb-3 pt-2">
            {showActions ? (
              <textarea
                value={editedPlan}
                onChange={(event) => setEditedPlan(event.target.value)}
                disabled={submitting}
                rows={8}
                className="mb-2 max-h-[220px] min-h-[120px] w-full resize-y rounded-xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)]/50 px-3 py-2 font-sans text-[12px] leading-5 text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/40"
                placeholder="计划 Markdown 正文"
              />
            ) : (
              <pre className="mb-2 max-h-[200px] overflow-auto whitespace-pre-wrap break-words rounded-xl border border-[var(--chat-border)]/30 bg-[var(--chat-surface-soft)]/40 px-3 py-2 font-sans text-[12px] leading-5 text-[var(--chat-text)]">
                {editedPlan || model.planContent || "(空计划)"}
              </pre>
            )}

            {showActions ? (
              <>
                <input
                  type="text"
                  disabled={submitting}
                  placeholder="可选：拒绝反馈或备注"
                  value={feedback}
                  onChange={(event) => setFeedback(event.target.value)}
                  className="mb-2 w-full rounded-xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface)] px-3 py-2 text-[12px] text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/40"
                />
                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    disabled={submitting}
                    onClick={() => void reject()}
                    className="rounded-xl border border-[var(--chat-border)]/50 px-3 py-1.5 text-[12px] font-medium text-[var(--chat-text)] transition-colors hover:bg-[var(--chat-interactive-hover)] disabled:opacity-60"
                  >
                    {submitting ? "处理中…" : "拒绝并修订"}
                  </button>
                  <button
                    type="button"
                    disabled={submitting || !canApprove}
                    onClick={() => void approve()}
                    className={[
                      "rounded-xl px-3 py-1.5 text-[12px] font-medium transition-colors",
                      canApprove && !submitting
                        ? "bg-[var(--chat-accent)] text-white hover:opacity-90"
                        : "cursor-not-allowed bg-[var(--chat-border)]/40 text-[var(--chat-text-soft)]",
                    ].join(" ")}
                  >
                    {submitting ? "处理中…" : "批准并开始实现"}
                  </button>
                </div>
              </>
            ) : null}
          </div>
        ) : null}
      </div>
    );
  }
);

PlanComposerBar.displayName = "PlanComposerBar";
PlanComposerBarInner.displayName = "PlanComposerBarInner";

export default PlanComposerBar;
