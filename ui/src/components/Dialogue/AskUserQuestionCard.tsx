import { FC, memo, useMemo, useState } from "react";
import { LoaderCircleIcon, MessageCircleQuestionIcon } from "lucide-react";
import { message } from "antd";
import { askUserApi, dispatchAskUserResume } from "@/services/askUser";

type QuestionOption = {
  label: string;
  description?: string;
};

type QuestionItem = {
  question: string;
  header?: string;
  options?: QuestionOption[];
  multiSelect?: boolean;
};

type AskUserQuestionCardProps = {
  tool: CHAT.Task;
};

function coerceQuestionsRaw(raw: unknown): unknown {
  if (Array.isArray(raw)) {
    return raw;
  }
  if (typeof raw === "string" && raw.trim()) {
    try {
      return JSON.parse(raw);
    } catch {
      return raw;
    }
  }
  return raw;
}

function asQuestions(raw: unknown): QuestionItem[] {
  const parsed = coerceQuestionsRaw(raw);
  if (!Array.isArray(parsed)) {
    return [];
  }
  return parsed
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      question: String(item.question || item.text || item.prompt || ""),
      header: item.header ? String(item.header) : undefined,
      multiSelect: Boolean(item.multiSelect),
      options: Array.isArray(item.options)
        ? item.options
            .filter((opt): opt is Record<string, unknown> => typeof opt === "object" && opt !== null)
            .map((opt) => ({
              label: String(opt.label || opt.value || ""),
              description: opt.description ? String(opt.description) : undefined,
            }))
        : [],
    }))
    .filter((item) => item.question);
}

/** 兼容 live SSE（questions 在 resultMap）与 restore 扁平结构（questions 在 tool 顶层） */
function resolveAskUserQuestions(tool: CHAT.Task): unknown {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  return (
    nested.questions ||
    resultMap.questions ||
    toolAny.questions ||
    undefined
  );
}

/**
 * AskUserQuestion 挂起卡片（continuation 版 HITL）。
 * 提交答案后 CAS 拿 resumeRequestId，再派发事件让 ChatView 连接 resume SSE。
 */
const AskUserQuestionCard: FC<AskUserQuestionCardProps> = memo(({ tool }) => {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  const questionId = String(
    nested.questionId || resultMap.questionId || toolAny.questionId || tool.messageId || ""
  );
  const questions = useMemo(() => asQuestions(resolveAskUserQuestions(tool)), [tool]);
  const status = String(
    nested.status || resultMap.status || toolAny.status || "pending"
  );
  const alreadyAnswered = status === "answered" || Boolean(resultMap.isFinal && status !== "pending");

  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [customText, setCustomText] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(alreadyAnswered);

  const allAnswered =
    questions.length > 0 &&
    questions.every((q) => {
      const value = (answers[q.question] || "").trim();
      return value.length > 0;
    });

  const selectOption = (question: QuestionItem, label: string) => {
    if (submitted || submitting) {
      return;
    }
    if (question.multiSelect) {
      setAnswers((prev) => {
        const current = (prev[question.question] || "")
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean);
        const next = current.includes(label)
          ? current.filter((item) => item !== label)
          : [...current, label];
        return { ...prev, [question.question]: next.join(", ") };
      });
      return;
    }
    setAnswers((prev) => ({ ...prev, [question.question]: label }));
    setCustomText((prev) => ({ ...prev, [question.question]: "" }));
  };

  const isSelected = (question: QuestionItem, label: string) => {
    const value = answers[question.question] || "";
    if (question.multiSelect) {
      return value
        .split(",")
        .map((s) => s.trim())
        .includes(label);
    }
    return value === label;
  };

  const submit = async () => {
    if (!questionId || !allAnswered || submitting || submitted) {
      return;
    }
    setSubmitting(true);
    try {
      const finalAnswers: Record<string, string> = { ...answers };
      for (const q of questions) {
        const custom = (customText[q.question] || "").trim();
        if (custom) {
          finalAnswers[q.question] = custom;
        }
      }
      const res = await askUserApi.answer({
        questionId,
        answers: finalAnswers,
      });
      if (res && res.accepted === false) {
        message.warning(String(res.message || "提交失败，问题可能已结束"));
        return;
      }
      setSubmitted(true);
      const resumeRequestId = String(res?.resumeRequestId || "");
      if (resumeRequestId) {
        dispatchAskUserResume({
          resumeRequestId,
          sessionId: String(
            nested.sessionId || resultMap.sessionId || toolAny.sessionId || ""
          ),
          questionId,
        });
        message.success("已提交，正在继续执行");
      } else {
        message.success("已提交");
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : "提交失败");
    } finally {
      setSubmitting(false);
    }
  };

  if (!questions.length) {
    return (
      <div className="mt-2 rounded-2xl border border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)]/50 px-4 py-3 text-[13px] text-[var(--chat-text-soft)]">
        等待用户回答（题目解析失败）
      </div>
    );
  }

  return (
    <div className="mt-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/50 bg-white px-4 py-3">
      <div className="mb-3 flex items-center gap-2">
        <div className="flex size-8 items-center justify-center rounded-xl border border-[var(--chat-border)]/40 bg-[#f5f5f7] text-[var(--chat-text-muted)]">
          {submitting ? (
            <LoaderCircleIcon className="size-4 animate-spin" />
          ) : (
            <MessageCircleQuestionIcon className="size-4" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-[14px] font-medium text-[var(--chat-text)]">
            {submitted ? "已回答，Agent 继续中…" : "需要你的选择"}
          </div>
          <div className="text-[12px] text-[var(--chat-text-soft)]">
            {submitted ? "答案已回传给智能体" : "回答后同一轮对话会自动继续"}
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-4">
        {questions.map((q) => (
          <div key={q.question} className="min-w-0">
            <div className="mb-1.5 flex items-center gap-2">
              {q.header ? (
                <span className="shrink-0 rounded-md border border-[var(--chat-border)]/40 bg-[#f5f5f7] px-1.5 py-0.5 text-[11px] font-medium text-[var(--chat-text-muted)]">
                  {q.header}
                </span>
              ) : null}
              <span className="text-[13px] font-medium text-[var(--chat-text)]">{q.question}</span>
              {q.multiSelect ? (
                <span className="text-[11px] text-[var(--chat-text-soft)]">多选</span>
              ) : null}
            </div>
            <div className="flex flex-col gap-1.5">
              {(q.options || []).map((opt) => {
                const selected = isSelected(q, opt.label);
                return (
                  <button
                    key={opt.label}
                    type="button"
                    disabled={submitted || submitting}
                    onClick={() => selectOption(q, opt.label)}
                    className={[
                      "rounded-xl border px-3 py-2 text-left transition-colors",
                      selected
                        ? "border-[#1d1d1f] bg-[#f5f5f7]"
                        : "border-[var(--chat-border)]/50 hover:border-[var(--chat-border)] hover:bg-[#fafafa]",
                      submitted || submitting ? "cursor-default opacity-80" : "cursor-pointer",
                    ].join(" ")}
                  >
                    <div className="text-[13px] font-medium text-[var(--chat-text)]">{opt.label}</div>
                    {opt.description ? (
                      <div className="mt-0.5 text-[12px] text-[var(--chat-text-soft)]">{opt.description}</div>
                    ) : null}
                  </button>
                );
              })}
              <input
                type="text"
                disabled={submitted || submitting}
                placeholder="或输入自定义回答（Other）"
                value={customText[q.question] || ""}
                onChange={(event) => {
                  const value = event.target.value;
                  setCustomText((prev) => ({ ...prev, [q.question]: value }));
                  if (value.trim()) {
                    setAnswers((prev) => ({ ...prev, [q.question]: value.trim() }));
                  }
                }}
                className="mt-1 w-full rounded-xl border border-[var(--chat-border)]/50 bg-white px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none focus:border-[#c7c7cc]"
              />
            </div>
          </div>
        ))}
      </div>

      {!submitted ? (
        <div className="mt-4 flex justify-end">
          <button
            type="button"
            disabled={!allAnswered || submitting}
            onClick={() => void submit()}
            className={[
              "rounded-xl px-4 py-2 text-[13px] font-medium transition-colors",
              allAnswered && !submitting
                ? "bg-[#1d1d1f] text-white hover:opacity-90"
                : "cursor-not-allowed bg-[#e8e8ed] text-[var(--chat-text-soft)]",
            ].join(" ")}
          >
            {submitting ? "提交中…" : "提交并继续"}
          </button>
        </div>
      ) : null}
    </div>
  );
});

AskUserQuestionCard.displayName = "AskUserQuestionCard";

export default AskUserQuestionCard;
