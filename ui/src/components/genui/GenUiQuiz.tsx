import { FC, memo, useMemo, useState } from "react";
import classNames from "classnames";
import { Check, ChevronRight, Lightbulb, RotateCcw, X } from "lucide-react";

type QuizOption = { id: string; label: string };

function normalizeOptions(raw: unknown): QuizOption[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item, i) => {
      if (typeof item === "string") return { id: String.fromCharCode(97 + i), label: item };
      if (!item || typeof item !== "object") return null;
      const o = item as Record<string, unknown>;
      return {
        id: String(o.id ?? o.value ?? String.fromCharCode(97 + i)),
        label: String(o.label ?? o.text ?? o.value ?? ""),
      };
    })
    .filter((x): x is QuizOption => Boolean(x && x.label));
}

function normalizeAnswer(raw: unknown): string[] {
  if (Array.isArray(raw)) return raw.map(String);
  if (raw == null || raw === "") return [];
  return [String(raw)];
}

export const GenUiQuiz: FC<{
  title?: string;
  prompt?: string;
  question?: string;
  options?: unknown;
  answer?: unknown;
  explanation?: string;
  multi?: boolean;
}> = memo(({ title, prompt, question, options, answer, explanation, multi }) => {
  const opts = useMemo(() => normalizeOptions(options), [options]);
  const answers = useMemo(() => normalizeAnswer(answer), [answer]);
  const [picked, setPicked] = useState<string[]>([]);
  const [revealed, setRevealed] = useState(false);

  const stem = prompt || question || title || "练习";
  const correct =
    revealed &&
    answers.length > 0 &&
    answers.every((a) => picked.includes(a)) &&
    picked.every((p) => answers.includes(p));

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)]">
      <div className="border-b border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)]/60 px-4 py-2.5">
        <div className="text-[11px] font-medium uppercase tracking-wide text-[var(--chat-accent)]">
          {multi ? "多选" : "测验"}
        </div>
        <div className="mt-0.5 text-[15px] font-semibold text-[var(--chat-text)]">{stem}</div>
      </div>
      <div className="space-y-2 p-3 sm:p-4">
        {opts.map((o) => {
          const on = picked.includes(o.id);
          const isAns = answers.includes(o.id);
          const show = revealed && (on || isAns);
          return (
            <button
              key={o.id}
              type="button"
              disabled={revealed}
              onClick={() => {
                setPicked((prev) => {
                  if (multi) {
                    return prev.includes(o.id) ? prev.filter((x) => x !== o.id) : [...prev, o.id];
                  }
                  return [o.id];
                });
              }}
              className={classNames(
                "flex w-full items-start gap-2 rounded-lg border px-3 py-2.5 text-left text-[13px] transition-colors",
                show && isAns && "border-emerald-400 bg-emerald-50 text-emerald-800",
                show && on && !isAns && "border-red-300 bg-red-50 text-red-700",
                !show && on && "border-[var(--chat-accent)] bg-[var(--chat-accent-soft)]",
                !show && !on && "border-[var(--chat-border)] bg-[var(--chat-surface)] hover:bg-[var(--chat-surface-soft)]",
                revealed && "cursor-default"
              )}
            >
              <span className="mt-0.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-current text-[11px] font-semibold uppercase">
                {o.id}
              </span>
              <span className="flex-1">{o.label}</span>
              {show && isAns ? <Check className="mt-0.5 size-4 shrink-0" /> : null}
              {show && on && !isAns ? <X className="mt-0.5 size-4 shrink-0" /> : null}
            </button>
          );
        })}

        <div className="flex flex-wrap items-center gap-2 pt-1">
          {!revealed ? (
            <button
              type="button"
              disabled={!picked.length || !answers.length}
              onClick={() => setRevealed(true)}
              className="inline-flex h-8 items-center rounded-lg bg-[var(--chat-accent)] px-3 text-[13px] font-medium text-white disabled:cursor-not-allowed disabled:opacity-40"
            >
              提交
            </button>
          ) : (
            <button
              type="button"
              onClick={() => {
                setPicked([]);
                setRevealed(false);
              }}
              className="inline-flex h-8 items-center gap-1 rounded-lg border border-[var(--chat-border)] px-3 text-[13px] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]"
            >
              <RotateCcw className="size-3.5" /> 重做
            </button>
          )}
          {revealed ? (
            <span
              className={classNames(
                "text-[13px] font-medium",
                correct ? "text-emerald-600" : "text-red-600"
              )}
            >
              {correct ? "回答正确" : "再看一看解析"}
            </span>
          ) : null}
        </div>

        {revealed && explanation ? (
          <div className="rounded-lg bg-[var(--chat-surface-soft)] px-3 py-2 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
            {explanation}
          </div>
        ) : null}
      </div>
    </div>
  );
});

GenUiQuiz.displayName = "GenUiQuiz";

type WorkedStep = { title: string; body: string; answer?: string };

export const GenUiWorkedExample: FC<{
  title?: string;
  problem?: string;
  prompt?: string;
  steps?: unknown;
  answer?: string;
}> = memo(({ title, problem, prompt, steps, answer }) => {
  const list = useMemo<WorkedStep[]>(() => {
    if (!Array.isArray(steps)) return [];
    return steps
      .map((item, i) => {
        if (typeof item === "string") return { title: `步骤 ${i + 1}`, body: item };
        if (!item || typeof item !== "object") return null;
        const o = item as Record<string, unknown>;
        return {
          title: String(o.title || o.label || `步骤 ${i + 1}`),
          body: String(o.body || o.caption || o.text || o.description || ""),
          answer: o.answer != null ? String(o.answer) : undefined,
        };
      })
      .filter((s): s is WorkedStep => Boolean(s && (s.body || s.answer)));
  }, [steps]);

  const [open, setOpen] = useState(0);
  const [showFinal, setShowFinal] = useState(false);

  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)]">
      <div className="border-b border-[var(--chat-border)]/40 bg-[var(--chat-surface-soft)]/60 px-4 py-2.5">
        <div className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wide text-[var(--chat-accent)]">
          <Lightbulb className="size-3.5" /> 例题
        </div>
        <div className="mt-0.5 text-[15px] font-semibold text-[var(--chat-text)]">
          {title || "逐步求解"}
        </div>
        {problem || prompt ? (
          <p className="mt-1 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
            {problem || prompt}
          </p>
        ) : null}
      </div>
      <div className="space-y-2 p-3 sm:p-4">
        {list.map((s, i) => {
          const visible = i <= open;
          if (!visible) {
            return (
              <button
                key={s.title + i}
                type="button"
                onClick={() => setOpen(i)}
                className="flex w-full items-center justify-between rounded-lg border border-dashed border-[var(--chat-border)] px-3 py-2 text-left text-[13px] text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)]"
              >
                揭示：{s.title}
                <ChevronRight className="size-3.5" />
              </button>
            );
          }
          return (
            <div
              key={s.title + i}
              className="rounded-lg border border-[var(--chat-border)]/50 bg-[var(--chat-surface-soft)]/40 px-3 py-2.5"
            >
              <div className="text-[12px] font-semibold text-[var(--chat-text)]">{s.title}</div>
              {s.body ? (
                <p className="mt-1 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">{s.body}</p>
              ) : null}
              {s.answer ? (
                <div className="mt-1.5 font-mono text-[13px] text-[var(--chat-accent)]">{s.answer}</div>
              ) : null}
            </div>
          );
        })}

        {answer ? (
          showFinal ? (
            <div className="rounded-lg bg-[var(--chat-accent-soft)] px-3 py-2 text-[13px] font-medium text-[var(--chat-accent)]">
              答案：{answer}
            </div>
          ) : (
            <button
              type="button"
              disabled={open < list.length - 1 && list.length > 0}
              onClick={() => {
                setOpen(list.length);
                setShowFinal(true);
              }}
              className="inline-flex h-8 items-center rounded-lg border border-[var(--chat-border)] px-3 text-[13px] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)] disabled:opacity-40"
            >
              显示最终答案
            </button>
          )
        ) : null}
      </div>
    </div>
  );
});

GenUiWorkedExample.displayName = "GenUiWorkedExample";
