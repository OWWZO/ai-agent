import { FC, memo, useMemo, useState } from "react";
import { LoaderCircleIcon, MessageCircleQuestionIcon } from "lucide-react";
import { message } from "antd";
import { askUserApi } from "@/services/askUser";

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

function asQuestions(raw: unknown): QuestionItem[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      question: String(item.question || ""),
      header: item.header ? String(item.header) : undefined,
      multiSelect: Boolean(item.multiSelect),
      options: Array.isArray(item.options)
        ? item.options
            .filter((opt): opt is Record<string, unknown> => typeof opt === "object" && opt !== null)
            .map((opt) => ({
              label: String(opt.label || ""),
              description: opt.description ? String(opt.description) : undefined,
            }))
        : [],
    }))
    .filter((item) => item.question);
}

/**
 * AskUserQuestion 挂起卡片（await 版 HITL）。
 * 用户提交后 POST /api/agent/ask-user/answer，唤醒后端 Agent 线程。
 */
const AskUserQuestionCard: FC<AskUserQuestionCardProps> = memo(({ tool }) => {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
  const questionId = String(nested.questionId || resultMap.questionId || tool.messageId || "");
  const questions = useMemo(
    () => asQuestions(nested.questions || resultMap.questions),
    [nested.questions, resultMap.questions]
  );
  const status = String(nested.status || resultMap.status || "pending");
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
      if (res && (res as { accepted?: boolean }).accepted === false) {
        message.warning(String((res as { message?: string }).message || "提交失败，问题可能已结束"));
        return;
      }
      setSubmitted(true);
      message.success("已提交，Agent 将继续执行");
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
    <div className="mt-2 overflow-hidden rounded-2xl border border-[var(--chat-accent)]/30 bg-[var(--chat-surface-soft)]/70 px-4 py-3 shadow-[var(--shadow-xs)]">
      <div className="mb-3 flex items-center gap-2">
        <div className="flex size-8 items-center justify-center rounded-xl bg-[var(--chat-accent)]/12 text-[var(--chat-accent)]">
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
                <span className="shrink-0 rounded-md bg-[var(--chat-accent)]/12 px-1.5 py-0.5 text-[11px] font-medium text-[var(--chat-accent)]">
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
                        ? "border-[var(--chat-accent)] bg-[var(--chat-accent)]/10"
                        : "border-[var(--chat-border)]/50 hover:border-[var(--chat-accent)]/40 hover:bg-[var(--chat-interactive-hover)]",
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
                className="mt-1 w-full rounded-xl border border-[var(--chat-border)]/50 bg-[var(--chat-surface)] px-3 py-2 text-[13px] text-[var(--chat-text)] outline-none focus:border-[var(--chat-accent)]/50"
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
                ? "bg-[var(--chat-accent)] text-white hover:opacity-90"
                : "cursor-not-allowed bg-[var(--chat-border)]/40 text-[var(--chat-text-soft)]",
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
