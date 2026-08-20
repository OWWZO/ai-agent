import { FC, memo, useEffect, useState } from "react";
import {
  ChevronUpIcon,
  LoaderCircleIcon,
  MinusIcon,
} from "lucide-react";
import { message } from "antd";
import {
  dispatchPlanApprovalResume,
  planApprovalApi,
} from "@/services/planApproval";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import { cn } from "@/lib/utils";

type PlanApprovalCardProps = {
  tool: CHAT.Task;
};

/**
 * ExitPlanMode 挂起卡片（对标 Kimi ApprovalCard plan_review）。
 * 用户批准/拒绝后 POST /api/agent/plan-approval/*，唤醒后端 Agent 线程。
 */
function pickPlanFields(tool: CHAT.Task) {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  const approvalId = String(
    nested.approvalId || resultMap.approvalId || toolAny.approvalId || tool.messageId || ""
  );
  const planContent = String(
    nested.planContent || resultMap.planContent || toolAny.planContent || ""
  );
  const planPath = String(
    nested.planPath || resultMap.planPath || toolAny.planPath || nested.path || resultMap.path || ""
  );
  const status = String(nested.status || resultMap.status || toolAny.status || "pending");
  return { resultMap, approvalId, planContent, planPath, status };
}

const PlanApprovalCard: FC<PlanApprovalCardProps> = memo(({ tool }) => {
  const { resultMap, approvalId, planContent, planPath, status } = pickPlanFields(tool);
  const alreadyDone =
    status === "approved" ||
    status === "rejected" ||
    status === "decided" ||
    Boolean(resultMap.isFinal && status !== "pending");

  const [minimized, setMinimized] = useState(false);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [editedPlan, setEditedPlan] = useState(planContent);
  const [submitting, setSubmitting] = useState(false);
  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const [decision, setDecision] = useState<"approved" | "rejected" | null>(
    status === "approved" || status === "rejected"
      ? (status as "approved" | "rejected")
      : null
  );

  useEffect(() => {
    setEditedPlan(planContent);
  }, [planContent, approvalId]);

  useEffect(() => {
    if (status === "approved" || status === "rejected") {
      setDecision(status);
    }
  }, [status]);

  const submitted = alreadyDone || decision !== null;
  const busy = submitting;

  const afterDecide = (
    kind: "approved" | "rejected",
    res:
      | {
          accepted?: boolean;
          resumeRequestId?: string;
          sessionId?: string;
          message?: string;
        }
      | undefined
  ) => {
    if (res && res.accepted === false) {
      message.warning(String(res.message || "操作失败，请求可能已结束"));
      return;
    }
    setDecision(kind);
    const resumeRequestId = String(res?.resumeRequestId || "");
    if (resumeRequestId) {
      const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
      dispatchPlanApprovalResume({
        resumeRequestId,
        sessionId: String(
          res?.sessionId || nested.sessionId || resultMap.sessionId || ""
        ),
        approvalId,
      });
      message.success(
        kind === "approved" ? "计划已批准，正在继续执行" : "已拒绝，正在继续修订"
      );
      return;
    }
    message.success(kind === "approved" ? "计划已批准" : "已拒绝");
  };

  const approve = async () => {
    if (!approvalId || busy || submitted) return;
    setSubmitting(true);
    setPendingAction("approvePlan");
    try {
      const res = await planApprovalApi.approve({
        approvalId,
        editedPlanContent: editedPlan !== planContent ? editedPlan : undefined,
        feedback: feedback.trim() || undefined,
      });
      afterDecide("approved", res);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "批准失败");
    } finally {
      setSubmitting(false);
      setPendingAction(null);
    }
  };

  const reject = async (label: string, fb?: string) => {
    if (!approvalId || busy || submitted) return;
    setSubmitting(true);
    setPendingAction(label);
    try {
      const res = await planApprovalApi.reject({
        approvalId,
        feedback: (fb ?? feedback).trim() || label,
      });
      afterDecide("rejected", res);
      setFeedbackOpen(false);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "拒绝失败");
    } finally {
      setSubmitting(false);
      setPendingAction(null);
    }
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (submitted || busy || minimized || feedbackOpen) return;
      const tag = (document.activeElement?.tagName ?? "").toLowerCase();
      if (tag === "input" || tag === "textarea") return;
      if (e.key === "1") {
        e.preventDefault();
        void approve();
      } else if (e.key === "2") {
        e.preventDefault();
        setFeedbackOpen(true);
      } else if (e.key === "3") {
        e.preventDefault();
        void reject("Reject and Exit");
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitted, busy, minimized, feedbackOpen, approvalId, editedPlan, feedback]);

  if (!approvalId) {
    return (
      <div className="kimi-ui-card kimi-appr">
        <div className="kimi-ui-card__head">
          <span className="kimi-appr-ic">!</span>
          <span className="kimi-appr-title">计划审批</span>
        </div>
        <div className="kimi-ui-card__body">等待计划批准（approvalId 缺失）</div>
      </div>
    );
  }

  const title =
    decision === "approved"
      ? "计划已批准"
      : decision === "rejected"
        ? "计划已拒绝"
        : "计划审批";

  return (
    <div className={cn("kimi-ui-card kimi-appr", minimized && "is-minimized")}>
      <div className="kimi-ui-card__head">
        <span className="kimi-appr-ic">!</span>
        <span className="kimi-appr-title">{title}</span>
        {!minimized && !submitted ? (
          <span className="kimi-appr-badge">需要确认</span>
        ) : null}
        {submitted ? (
          <span className="kimi-appr-badge">
            {decision === "rejected" || status === "rejected"
              ? "只读 · 已拒绝"
              : "只读 · 已批准"}
          </span>
        ) : null}
        <button
          type="button"
          className="kimi-appr-min"
          aria-label={minimized ? "展开" : "收起"}
          onClick={() => setMinimized((v) => !v)}
        >
          {minimized ? (
            <ChevronUpIcon className="size-4" />
          ) : (
            <MinusIcon className="size-4" />
          )}
        </button>
      </div>

      {!minimized ? (
        <>
          <div className="kimi-ui-card__body">
            {planPath ? <div className="kimi-appr-path">{planPath}</div> : null}
            <div className="kimi-appr-plan">
              {submitted ? (
                <MarkdownRenderer
                  markDownContent={editedPlan || planContent || "(空计划)"}
                  className="chat-markdown kimi-md text-[14px] leading-relaxed"
                />
              ) : (
                <textarea
                  value={editedPlan}
                  onChange={(event) => setEditedPlan(event.target.value)}
                  disabled={busy}
                  rows={12}
                  placeholder="计划 Markdown 正文（可编辑后批准）"
                />
              )}
            </div>

            {feedbackOpen && !submitted ? (
              <div className="kimi-appr-feedback">
                <textarea
                  value={feedback}
                  onChange={(event) => setFeedback(event.target.value)}
                  rows={2}
                  placeholder="说明需要如何修订计划…"
                  disabled={busy}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      void reject("Revise", feedback);
                    } else if (e.key === "Escape") {
                      e.preventDefault();
                      setFeedbackOpen(false);
                      setFeedback("");
                    }
                  }}
                />
                <div className="kimi-appr-hint">Enter 提交修订 · Esc 取消</div>
              </div>
            ) : null}
          </div>

          {!submitted ? (
            <div className="kimi-ui-card__foot">
              <div className="kimi-appr-actions">
                <button
                  type="button"
                  className="kimi-appr-btn is-primary"
                  disabled={busy || !(editedPlan || planContent).trim()}
                  onClick={() => void approve()}
                >
                  {pendingAction === "approvePlan" ? (
                    <LoaderCircleIcon className="size-3.5 animate-spin" />
                  ) : null}
                  批准计划
                  <span className="kimi-appr-kbd">1</span>
                </button>
                <button
                  type="button"
                  className="kimi-appr-btn"
                  disabled={busy}
                  onClick={() => setFeedbackOpen(true)}
                >
                  修订
                  <span className="kimi-appr-kbd">2</span>
                </button>
                <button
                  type="button"
                  className="kimi-appr-btn is-danger"
                  disabled={busy}
                  onClick={() => void reject("Reject and Exit")}
                >
                  {pendingAction === "Reject and Exit" ? (
                    <LoaderCircleIcon className="size-3.5 animate-spin" />
                  ) : null}
                  拒绝并退出
                  <span className="kimi-appr-kbd">3</span>
                </button>
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
});

PlanApprovalCard.displayName = "PlanApprovalCard";

export default PlanApprovalCard;
