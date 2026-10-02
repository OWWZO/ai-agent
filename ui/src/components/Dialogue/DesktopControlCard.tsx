import { FC, memo, useMemo, useState } from "react";
import { LoaderCircleIcon, PanelRightOpen } from "lucide-react";
import { message } from "antd";
import {
  desktopControlApi,
  dispatchDesktopControlPreview,
  dispatchDesktopControlResume,
} from "@/services/desktopControl";

type DesktopControlCardProps = {
  tool: CHAT.Task;
};

function readDesktopFields(tool: CHAT.Task) {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  return {
    resultMap,
    nested,
    controlId: String(
      nested.controlId || resultMap.controlId || toolAny.controlId || tool.messageId || ""
    ),
    reason: String(nested.reason || resultMap.reason || toolAny.reason || ""),
    streamUrl: String(nested.streamUrl || resultMap.streamUrl || toolAny.streamUrl || ""),
    status: String(nested.status || resultMap.status || toolAny.status || "pending"),
    sessionId: String(nested.sessionId || resultMap.sessionId || toolAny.sessionId || ""),
  };
}

const DesktopControlCard: FC<DesktopControlCardProps> = memo(({ tool }) => {
  const fields = useMemo(() => readDesktopFields(tool), [tool]);
  const alreadyDone = fields.status === "completed" || Boolean(fields.resultMap.isFinal);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(alreadyDone);
  const busy = submitting;
  const done = submitted || alreadyDone;

  const openDesktop = () => {
    if (!fields.streamUrl) {
      message.warning("桌面地址不可用，请刷新后重试");
      return;
    }
    dispatchDesktopControlPreview();
  };

  const complete = async () => {
    if (!fields.controlId || busy || done) return;
    setSubmitting(true);
    try {
      const res = await desktopControlApi.complete({ controlId: fields.controlId });
      if (res && res.accepted === false) {
        message.warning(String(res.message || "提交失败，桌面控制可能已结束"));
        return;
      }
      setSubmitted(true);
      const resumeRequestId = String(res?.resumeRequestId || "");
      if (resumeRequestId) {
        dispatchDesktopControlResume({
          resumeRequestId,
          sessionId: fields.sessionId,
          controlId: fields.controlId,
        });
        message.success("已完成，正在继续执行");
      } else {
        message.success("已完成");
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : "提交失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="kimi-ui-card kimi-qcard" data-testid="desktop-control-card">
      <div className="kimi-ui-card__head">
        <span className="kimi-qcard-ic">🖥</span>
        <span className="kimi-qcard-title">桌面控制</span>
        <span className="kimi-appr-badge" style={{ marginLeft: "auto" }}>
          {done ? "已完成" : "待操作"}
        </span>
      </div>
      <div className="kimi-ui-card__body flex flex-col gap-2">
        {fields.reason ? <div className="text-[13px] leading-6">{fields.reason}</div> : null}
        <div className="flex flex-wrap gap-2">
          {fields.streamUrl && !done ? (
            <button
              type="button"
              className="inline-flex h-8 items-center gap-1.5 rounded-[6px] bg-[var(--color-hover)] px-3 text-[13px]"
              onClick={openDesktop}
            >
              <PanelRightOpen className="h-3.5 w-3.5" aria-hidden="true" />
              在工作区查看
            </button>
          ) : null}
          <button
            type="button"
            className="inline-flex h-8 items-center gap-1 rounded-[6px] bg-[var(--color-text)] px-3 text-[13px] text-[var(--color-bg)] disabled:opacity-50"
            disabled={!fields.controlId || busy || done}
            onClick={() => void complete()}
          >
            {busy ? <LoaderCircleIcon className="h-3.5 w-3.5 animate-spin" /> : null}
            {done ? "已完成" : "我已完成"}
          </button>
        </div>
      </div>
    </div>
  );
});

DesktopControlCard.displayName = "DesktopControlCard";

export default DesktopControlCard;

export function readDesktopStreamUrl(tool?: CHAT.Task | null): string {
  if (!tool) return "";
  return readDesktopFields(tool).streamUrl;
}

export function isPendingDesktopControl(tool?: CHAT.Task | null): boolean {
  if (!tool || tool.messageType !== "desktop_control") return false;
  const status = readDesktopFields(tool).status;
  return status === "pending" || !status;
}
