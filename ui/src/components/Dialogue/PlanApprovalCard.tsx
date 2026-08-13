import { FC, memo, useEffect, useState } from "react";
import { ClipboardCheckIcon, LoaderCircleIcon } from "lucide-react";
import { message } from "antd";
import { planApprovalApi } from "@/services/planApproval";

type PlanApprovalCardProps = {
  tool: CHAT.Task;
};

/**
 * ExitPlanMode 挂起卡片（对标 cc-haha Ready to code?）。
 * 用户批准/拒绝后 POST /api/agent/plan-approval/*，唤醒后端 Agent 线程。
 */
function pickPlanFields(tool: CHAT.Task) {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  // buildTaskFromEventData 会展开 eventData.resultMap，字段可能在顶层 / resultMap / 嵌套 resultMap
  const toolAny = tool as unknown as Record<string, unknown>;
  const approvalId = String(
    nested.approvalId || resultMap.approvalId || toolAny.approvalId || tool.messageId || ""
  );
  const planContent = String(
    nested.planContent || resultMap.planContent || toolAny.planContent || ""
  );
  const status = String(nested.status || resultMap.status || toolAny.status || "pending");
  return { resultMap, approvalId, planContent, status };
}

const PlanApprovalCard: FC<PlanApprovalCardProps> = memo(({ tool }) => {
  const { resultMap, approvalId, planContent, status } = pickPlanFields(tool);
  const alreadyDone = status === "approved" || status === "rejected" || Boolean(resultMap.isFinal && status !== "pending");

  const [feedback, setFeedback] = useState("");
  const [editedPlan, setEditedPlan] = useState(planContent);
  const [submitting, setSubmitting] = useState(false);
  const [decision, setDecision] = useState<"approved" | "rejected" | null>(
    status === "approved" || status === "rejected" ? (status as "approved" | "rejected") : null
  );

  useEffect(() => {
    if (status === "approved" || status === "rejected") {
      setDecision(status);
    }
  }, [status]);

  const submitted = alreadyDone || decision !== null;

  const approve = async () => {
    if (!approvalId || submitting || submitted) {
      return;
    }
    setSubmitting(true);
    try {
      const res = await planApprovalApi.approve({
        approvalId,
        editedPlanContent: editedPlan !== planContent ? editedPlan : undefined,
        feedback: feedback.trim() || undefined,
      });
      if (res && (res as { accepted?: boolean }).accepted === false) {
        message.warning(String((res as { message?: string }).message || "批准失败，请求可能已结束"));
        return;
      }
      setDecision("approved");
      message.success("计划已批准，Agent 将开始实现");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "批准失败");
    } finally {
      setSubmitting(false);
    }
  };

  const reject = async () => {
    if (!approvalId || submitting || submitted) {
      return;
    }
    setSubmitting(true);
    try {
      const res = await planApprovalApi.reject({
        approvalId,
        feedback: feedback.trim() || "需要修订计划",
      });
      if (res && (res as { accepted?: boolean }).accepted === false) {
        message.warning(String((res as { message?: string }).message || "拒绝失败，请求可能已结束"));
        return;
      }
      setDecision("rejected");
      message.success("已拒绝，Agent 将继续修订计划");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "拒绝失败");
    } finally {
      setSubmitting(false);
    }
  };

  if (!approvalId) {
    return (
      <div className="mt-2 rounded-2xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)]/50 px-4 py-3 text-[13px] text-[var(--chat-text-soft)]">
        等待计划批准（approvalId 缺失）
      </div>
    );
  }

  const title =
    decision === "approved"
      ? "计划已批准，开始实现…"
      : decision === "rejected"
        ? "计划已拒绝，修订中…"
        : "Ready to code? 批准实现计划";

  const subtitle =
    decision === "approved"
      ? "批准结果已回传给智能体"
      : decision === "rejected"
        ? "反馈已回传，仍停留在 Plan Mode"
        : "审查计划后批准或拒绝；拒绝后 Agent 会继续改计划";

  const readOnly = submitted;

  return (
    <div className="mt-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/50 bg-[var(--chat-surface)] px-4 py-3">
      <div className="mb-3 flex items-center gap-2">
        <div className="flex size-8 items-center justify-center rounded-xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)] text-[var(--chat-text-muted)]">
          {submitting ? (
            <LoaderCircleIcon className="size-4 animate-spin" />
          ) : (
            <ClipboardCheckIcon className="size-4" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <div className="text-[14px] font-medium text-[var(--chat-text)]">{title}</div>
            {readOnly ? (
              <span className="rounded-full bg-[var(--chat-surface-muted)] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-soft)]">
                {decision === "rejected" || status === "rejected" ? "只读 · 已拒绝" : "只读 · 已批准"}
              </span>
            ) : null}
          </div>
          <div className="text-[12px] text-[var(--chat-text-soft)]">{subtitle}</div>
        </div>
      </div>

      <div className="max-h-[320px] overflow-auto rounded-xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)]/40 px-3 py-2">
        {readOnly ? (
          <pre className="whitespace-pre-wrap break-words font-sans text-[12px] leading-5 text-[var(--chat-text)]">
            {editedPlan || planContent || "(空计划)"}
          </pre>
        ) : (
          <textarea
            value={editedPlan}
            onChange={(event) => setEditedPlan(event.target.value)}
            disabled={submitting}
            rows={12}
            className="w-full resize-y bg-transparent font-sans text-[12px] leading-5 text-[var(--chat-text)] outline-none"
            placeholder="计划 Markdown 正文（可编辑后批准）"
          />
        )}
      </div>

      {!readOnly ? (
        <>
          <input
            type="text"
            disabled={submitting}
            placeholder="可选：拒绝反馈或备注"
            value={feedback}
            onChange={(event) => setFeedback(event.target.value)}
            className="mt-3 w-full rounded-xl border border-[var(--chat-border)]/50 bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none focus:border-[var(--chat-border-strong)]"
          />
          <div className="mt-3 flex justify-end gap-2">
            <button
              type="button"
              disabled={submitting}
              onClick={() => void reject()}
              className="rounded-xl border border-[var(--chat-border)]/50 px-4 py-2 text-[13px] font-medium text-[var(--chat-text)] transition-colors hover:bg-[var(--chat-interactive-hover)] disabled:opacity-60"
            >
              {submitting ? "处理中…" : "拒绝并修订"}
            </button>
            <button
              type="button"
              disabled={submitting || !(editedPlan || planContent).trim()}
              onClick={() => void approve()}
              className={[
                "rounded-xl px-4 py-2 text-[13px] font-medium transition-colors",
                !submitting && (editedPlan || planContent).trim()
                  ? "bg-[var(--chat-text)] text-[var(--chat-bg)] hover:opacity-90"
                  : "cursor-not-allowed bg-[var(--chat-surface-muted)] text-[var(--chat-text-soft)]",
              ].join(" ")}
            >
              {submitting ? "处理中…" : "批准并开始实现"}
            </button>
          </div>
        </>
      ) : null}
    </div>
  );
});

PlanApprovalCard.displayName = "PlanApprovalCard";

export default PlanApprovalCard;
