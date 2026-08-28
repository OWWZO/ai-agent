import { FC, memo, useEffect, useMemo, useState } from "react";
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
import {
  buildComposerPlanModel,
  type ComposerPlanModel,
} from "./planComposerModel";
import { cn } from "@/lib/utils";

type PlanComposerBarProps = {
  chat?: CHAT.ChatItem;
  taskList?: CHAT.Task[];
  structuredPlan?: CHAT.Plan;
  loading?: boolean;
};

/**
 * 依附在输入框上方的计划条（ExitPlanMode / PlanSolve stages）。
 * plan_approval 源对齐 Kimi ApprovalCard plan_review 视觉。
 */
const PlanComposerBar: FC<PlanComposerBarProps> = memo(
  ({ chat, taskList, structuredPlan, loading }) => {
    const model = useMemo(
      () =>
        buildComposerPlanModel({
          chat,
          taskList,
          structuredPlan,
        }),
      [chat, taskList, structuredPlan]
    );

    if (!model) {
      return null;
    }

    return <PlanComposerBarInner model={model} loading={loading} />;
  }
);

const PlanComposerBarInner: FC<{
  model: ComposerPlanModel;
  loading?: boolean;
}> = memo(({ model, loading }) => {
  const [minimized, setMinimized] = useState(false);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [editedPlan, setEditedPlan] = useState(model.planContent);
  const [submitting, setSubmitting] = useState(false);
  const [pendingAction, setPendingAction] = useState<string | null>(null);
  const [localStatus, setLocalStatus] = useState(model.status);

  useEffect(() => {
    setEditedPlan(model.planContent);
    setLocalStatus(model.status);
    setFeedback("");
    setFeedbackOpen(false);
    if (model.planContent.trim() && model.source === "plan_approval") {
      setMinimized(false);
    }
  }, [model.approvalId, model.planContent, model.source, model.status]);

  const status = localStatus || model.status || "pending";
  const isDecided =
    status === "approved" ||
    status === "rejected" ||
    status === "decided" ||
    status === "cancelled" ||
    status === "timeout";
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
  const isApprovalSkin = model.source === "plan_approval";

  let title = isApprovalSkin ? "按这份计划开始执行？" : model.title;
  if (status === "approved" || (status === "decided" && model.approved !== false)) {
    title = "计划已批准";
  } else if (status === "rejected" || (status === "decided" && model.approved === false)) {
    title = "计划已拒绝";
  }

  const approve = async () => {
    if (!model.approvalId || submitting || !canApprove) return;
    setSubmitting(true);
    setPendingAction("approvePlan");
    try {
      const res = await planApprovalApi.approve({
        approvalId: model.approvalId,
        editedPlanContent:
          editedPlan !== model.planContent ? editedPlan : undefined,
        feedback: feedback.trim() || undefined,
      });
      if (res && res.accepted === false) {
        message.warning(String(res.message || "批准失败"));
        return;
      }
      setLocalStatus("approved");
      const resumeRequestId = String(res?.resumeRequestId || "");
      if (resumeRequestId) {
        dispatchPlanApprovalResume({
          resumeRequestId,
          sessionId: String(res?.sessionId || ""),
          approvalId: model.approvalId,
          approved: true,
        });
        message.success("计划已批准，正在继续执行");
      } else {
        message.success("计划已批准");
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : "批准失败");
    } finally {
      setSubmitting(false);
      setPendingAction(null);
    }
  };

  const reject = async (label: string, fb?: string) => {
    if (!model.approvalId || submitting || status !== "pending") return;
    setSubmitting(true);
    setPendingAction(label);
    try {
      const res = await planApprovalApi.reject({
        approvalId: model.approvalId,
        feedback: (fb ?? feedback).trim() || label,
      });
      if (res && res.accepted === false) {
        message.warning(String(res.message || "拒绝失败"));
        return;
      }
      setLocalStatus("rejected");
      setFeedbackOpen(false);
      const resumeRequestId = String(res?.resumeRequestId || "");
      if (resumeRequestId) {
        dispatchPlanApprovalResume({
          resumeRequestId,
          sessionId: String(res?.sessionId || ""),
          approvalId: model.approvalId,
          approved: false,
        });
        message.success("已拒绝，正在继续修订");
      } else {
        message.success("已拒绝");
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : "拒绝失败");
    } finally {
      setSubmitting(false);
      setPendingAction(null);
    }
  };

  if (isApprovalSkin) {
    return (
      <div
        className={cn(
          "kimi-ui-card kimi-appr mb-2",
          minimized && "is-minimized"
        )}
      >
        <div className="kimi-ui-card__head">
          <span className="kimi-appr-ic">!</span>
          <span className="kimi-appr-title">{title}</span>
          {!minimized && showActions ? (
            <span className="kimi-appr-badge">需要确认</span>
          ) : null}
          {isDecided ? (
            <span className="kimi-appr-badge">
              {status === "rejected" || model.approved === false
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
              {model.planFilePath ? (
                <div className="kimi-appr-path" title={model.planFilePath}>
                  {model.planFilePath}
                </div>
              ) : null}
              <div className="kimi-appr-plan">
                {showActions ? (
                  <>
                    <div className="kimi-appr-label">计划内容</div>
                    <textarea
                      value={editedPlan}
                      onChange={(event) => setEditedPlan(event.target.value)}
                      disabled={submitting}
                      rows={10}
                      placeholder="计划 Markdown 正文"
                    />
                  </>
                ) : (
                  <MarkdownRenderer
                    markDownContent={editedPlan || model.planContent || "(空计划)"}
                  />
                )}
              </div>

              {feedbackOpen && showActions ? (
                <div className="kimi-appr-feedback">
                  <textarea
                    value={feedback}
                    onChange={(event) => setFeedback(event.target.value)}
                    rows={2}
                    placeholder="说明需要如何修订计划…"
                    disabled={submitting}
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

            {showActions ? (
              <div className="kimi-ui-card__foot">
                <div className="kimi-appr-actions">
                  <button
                    type="button"
                    className="kimi-appr-btn is-primary"
                    disabled={submitting || !canApprove}
                    onClick={() => void approve()}
                  >
                    {pendingAction === "approvePlan" ? (
                      <LoaderCircleIcon className="size-3.5 animate-spin" />
                    ) : null}
                    执行计划
                    <span className="kimi-appr-kbd">1</span>
                  </button>
                  <button
                    type="button"
                    className="kimi-appr-btn"
                    disabled={submitting}
                    onClick={() => setFeedbackOpen(true)}
                  >
                    修改计划
                    <span className="kimi-appr-kbd">2</span>
                  </button>
                  <button
                    type="button"
                    className="kimi-appr-btn is-danger"
                    disabled={submitting}
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
  }

  // structured_plan：轻量折叠条，不伪装审批卡
  return (
    <div className="kimi-ui-card mb-2">
      <button
        type="button"
        onClick={() => setMinimized((v) => !v)}
        className="flex w-full items-center gap-2 px-3.5 py-2.5 text-left"
      >
        <span className="kimi-appr-ic" style={{ color: "var(--color-accent)" }}>
          {loading && status === "planning" ? (
            <LoaderCircleIcon className="size-4 animate-spin" />
          ) : (
            "≡"
          )}
        </span>
        <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-[var(--color-text)]">
          {model.title}
        </span>
        <span className="shrink-0 rounded-full border border-[var(--color-line)] px-2 py-0.5 text-[11px] text-[var(--color-text-muted)]">
          {status === "planning" ? "规划中" : "进行中"}
        </span>
        {minimized ? (
          <ChevronUpIcon className="size-4 text-[var(--color-text-muted)]" />
        ) : (
          <MinusIcon className="size-4 text-[var(--color-text-muted)]" />
        )}
      </button>
      {!minimized ? (
        <div className="kimi-ui-card__body border-t border-[var(--color-line)]">
          <pre className="max-h-[200px] overflow-auto whitespace-pre-wrap break-words font-sans text-[12px] leading-5 text-[var(--color-text)]">
            {editedPlan || model.planContent || "(空计划)"}
          </pre>
        </div>
      ) : null}
    </div>
  );
});

PlanComposerBar.displayName = "PlanComposerBar";
PlanComposerBarInner.displayName = "PlanComposerBarInner";

export default PlanComposerBar;
