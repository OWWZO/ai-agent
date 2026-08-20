import {
  FC,
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { LoaderCircleIcon, MinusIcon, ChevronUpIcon } from "lucide-react";
import { message } from "antd";
import { askUserApi, dispatchAskUserResume } from "@/services/askUser";
import {
  normalizeAskQuestions,
  type AskQuestion,
} from "./tools/askUserToolParse";
import { cn } from "@/lib/utils";

type AskUserQuestionCardProps = {
  tool: CHAT.Task;
};

function resolveAskUserQuestions(tool: CHAT.Task): unknown {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  return nested.questions || resultMap.questions || toolAny.questions || undefined;
}

function resolveAskFields(tool: CHAT.Task) {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  return {
    resultMap,
    nested,
    toolAny,
    questionId: String(
      nested.questionId || resultMap.questionId || toolAny.questionId || tool.messageId || ""
    ),
    status: String(nested.status || resultMap.status || toolAny.status || "pending"),
  };
}

/**
 * AskUserQuestion 挂起卡片（continuation 版 HITL）。
 * 视觉对齐 Kimi QuestionCard；提交后 CAS 拿 resumeRequestId，再派发 resume SSE。
 */
const AskUserQuestionCard: FC<AskUserQuestionCardProps> = memo(({ tool }) => {
  const { resultMap, nested, toolAny, questionId, status } = resolveAskFields(tool);
  const questions = useMemo(
    () => normalizeAskQuestions(resolveAskUserQuestions(tool)),
    [tool]
  );
  const alreadyAnswered =
    status === "answered" || Boolean(resultMap.isFinal && status !== "pending");

  const [step, setStep] = useState(0);
  const [minimized, setMinimized] = useState(false);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [customText, setCustomText] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(alreadyAnswered);
  const otherInputRef = useRef<HTMLInputElement | null>(null);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setStep(0);
    setMinimized(false);
    setAnswers({});
    setCustomText({});
    setSubmitted(alreadyAnswered);
  }, [questionId, alreadyAnswered]);

  useEffect(() => {
    if (step >= questions.length) setStep(0);
  }, [questions.length, step]);

  const current = questions[step];
  const total = questions.length;
  const busy = submitting;

  const isQuestionAnswered = useCallback(
    (q: AskQuestion) => {
      const custom = (customText[q.question] || "").trim();
      if (custom) return true;
      return (answers[q.question] || "").trim().length > 0;
    },
    [answers, customText]
  );

  const allAnswered =
    questions.length > 0 && questions.every((q) => isQuestionAnswered(q));
  const currentAnswered = current ? isQuestionAnswered(current) : false;

  const selectOption = (question: AskQuestion, label: string) => {
    if (submitted || busy) return;
    if (question.multiSelect) {
      setAnswers((prev) => {
        const currentVals = (prev[question.question] || "")
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean);
        const next = currentVals.includes(label)
          ? currentVals.filter((item) => item !== label)
          : [...currentVals, label];
        return { ...prev, [question.question]: next.join(", ") };
      });
      return;
    }
    setAnswers((prev) => {
      if (prev[question.question] === label) {
        const next = { ...prev };
        delete next[question.question];
        return next;
      }
      return { ...prev, [question.question]: label };
    });
    setCustomText((prev) => ({ ...prev, [question.question]: "" }));
  };

  const isSelected = (question: AskQuestion, label: string) => {
    const value = answers[question.question] || "";
    if (question.multiSelect) {
      return value
        .split(",")
        .map((s) => s.trim())
        .includes(label);
    }
    return value === label && !(customText[question.question] || "").trim();
  };

  const isOtherSelected = (question: AskQuestion) =>
    Boolean((customText[question.question] || "").trim());

  const glyphFor = (multi: boolean, on: boolean) =>
    multi ? (on ? "■" : "□") : on ? "●" : "○";

  const submit = async () => {
    if (!questionId || !allAnswered || busy || submitted) return;
    setSubmitting(true);
    try {
      const finalAnswers: Record<string, string> = { ...answers };
      for (const q of questions) {
        const custom = (customText[q.question] || "").trim();
        if (custom) finalAnswers[q.question] = custom;
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

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (submitted || busy || minimized) return;
      // 仅当焦点在本卡片内时响应，避免 Esc/数字键误伤主对话或 cancel 断链
      const root = rootRef.current;
      if (!root || !root.contains(document.activeElement)) return;

      const tag = (document.activeElement?.tagName ?? "").toLowerCase();
      const inField = tag === "input" || tag === "textarea";

      if (e.key === "Enter" && !inField) {
        e.preventDefault();
        if (step < total - 1 && currentAnswered) {
          setStep((s) => s + 1);
        } else if (allAnswered) {
          void submit();
        }
        return;
      }
      if (inField) return;
      if (e.key === "Escape") {
        e.preventDefault();
        setMinimized(true);
        return;
      }
      const num = Number.parseInt(e.key, 10);
      if (!Number.isNaN(num) && num >= 1 && num <= 9 && current) {
        e.preventDefault();
        const opt = current.options[num - 1];
        if (opt) selectOption(current, opt.label);
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    submitted,
    busy,
    minimized,
    step,
    total,
    currentAnswered,
    allAnswered,
    current,
  ]);

  if (!questions.length) {
    return (
      <div className="kimi-ui-card kimi-qcard">
        <div className="kimi-ui-card__head">
          <span className="kimi-qcard-ic">?</span>
          <span className="kimi-qcard-title">提问</span>
        </div>
        <div className="kimi-ui-card__body">等待用户回答（题目解析失败）</div>
      </div>
    );
  }

  if (submitted) {
    return (
      <div className="kimi-ui-card kimi-qcard">
        <div className="kimi-ui-card__head">
          <span className="kimi-qcard-ic">?</span>
          <span className="kimi-qcard-title">已回答</span>
        </div>
        <div className="kimi-ui-card__body">答案已回传，Agent 继续中…</div>
      </div>
    );
  }

  return (
    <div
      ref={rootRef}
      className={cn("kimi-ui-card kimi-qcard", minimized && "is-minimized")}
      tabIndex={-1}
    >
      <div className="kimi-ui-card__head">
        <span className="kimi-qcard-ic">?</span>
        <span className="kimi-qcard-title">提问</span>
        {total > 1 && !minimized ? (
          <span className="kimi-qcard-step">
            {step + 1}/{total}
          </span>
        ) : null}
        {minimized && current ? (
          <span className="kimi-qcard-peek">{current.question}</span>
        ) : null}
        <button
          type="button"
          className="kimi-qcard-min"
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

      {!minimized && current ? (
        <>
          <div className="kimi-ui-card__body">
            {total > 1 ? (
              <div className="kimi-qcard-steps" role="tablist">
                {questions.map((q, i) => (
                  <button
                    key={`${q.question}-${i}`}
                    type="button"
                    className={cn(
                      "kimi-qcard-dot",
                      i === step && "is-active",
                      isQuestionAnswered(q) && "is-answered"
                    )}
                    aria-selected={i === step}
                    onClick={() => setStep(i)}
                  >
                    {i + 1}
                  </button>
                ))}
              </div>
            ) : null}

            {current.header ? (
              <div className="kimi-qcard-chip">{current.header}</div>
            ) : null}
            <div className="kimi-qcard-text">{current.question}</div>

            <div className="kimi-qcard-opts">
              {current.options.map((opt, oi) => {
                const selected = isSelected(current, opt.label);
                return (
                  <button
                    key={`${opt.label}-${oi}`}
                    type="button"
                    disabled={busy}
                    className={cn("kimi-qcard-opt", selected && "is-selected")}
                    onClick={() => selectOption(current, opt.label)}
                  >
                    <span className="kimi-qcard-opt-key">{oi + 1}</span>
                    <span className="kimi-qcard-opt-glyph">
                      {glyphFor(current.multiSelect, selected)}
                    </span>
                    <span className="kimi-qcard-opt-text">
                      <span className="kimi-qcard-opt-label">{opt.label}</span>
                      {opt.description ? (
                        <span className="kimi-qcard-opt-desc">
                          {opt.description}
                        </span>
                      ) : null}
                    </span>
                  </button>
                );
              })}

              <button
                type="button"
                disabled={busy}
                className={cn(
                  "kimi-qcard-opt",
                  isOtherSelected(current) && "is-selected"
                )}
                onClick={() => {
                  otherInputRef.current?.focus();
                }}
              >
                <span className="kimi-qcard-opt-key" />
                <span className="kimi-qcard-opt-glyph">
                  {glyphFor(current.multiSelect, isOtherSelected(current))}
                </span>
                <span className="kimi-qcard-opt-label">其他</span>
                <input
                  ref={otherInputRef}
                  type="text"
                  disabled={busy}
                  className="kimi-qcard-other"
                  placeholder="其他"
                  value={customText[current.question] || ""}
                  onChange={(event) => {
                    const value = event.target.value;
                    setCustomText((prev) => ({
                      ...prev,
                      [current.question]: value,
                    }));
                    if (!current.multiSelect) {
                      setAnswers((prev) => {
                        const next = { ...prev };
                        delete next[current.question];
                        return next;
                      });
                    }
                  }}
                  onClick={(event) => event.stopPropagation()}
                />
              </button>
            </div>
          </div>

          <div className="kimi-ui-card__foot">
            <div className="kimi-qcard-foot">
              {step < total - 1 ? (
                <button
                  type="button"
                  className="kimi-qcard-btn is-primary"
                  disabled={!currentAnswered || busy}
                  onClick={() => setStep((s) => s + 1)}
                >
                  下一题
                </button>
              ) : (
                <button
                  type="button"
                  className="kimi-qcard-btn is-primary"
                  disabled={!allAnswered || busy}
                  onClick={() => void submit()}
                >
                  {submitting ? (
                    <LoaderCircleIcon className="size-3.5 animate-spin" />
                  ) : null}
                  {submitting ? "提交中…" : "提交"}
                </button>
              )}
              {total > 1 ? (
                <button
                  type="button"
                  className="kimi-qcard-btn"
                  disabled={step === 0 || busy}
                  onClick={() => setStep((s) => Math.max(0, s - 1))}
                >
                  上一题
                </button>
              ) : null}
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
});

AskUserQuestionCard.displayName = "AskUserQuestionCard";

export default AskUserQuestionCard;
