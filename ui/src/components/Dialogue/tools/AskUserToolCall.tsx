import { memo, useEffect, useMemo, useState } from "react";
import { HelpCircleIcon } from "lucide-react";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import {
  answerFor,
  normalizeAskQuestions,
  parseAskAnswersRecord,
  parseAskInput,
  parseAskOutput,
  resolveAnswer,
} from "./askUserToolParse";
import {
  formatDurationLabel,
  resolveTaskToolArg,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "./toolTaskAdapter";
import { cn } from "@/lib/utils";

type AskUserToolCallProps = {
  tool: CHAT.Task;
  durationMs?: number;
  durationLabel?: string;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
};

function clip(s: string, max = 80): string {
  const trimmed = s.trim();
  return trimmed.length > max ? `${trimmed.slice(0, max - 1)}…` : trimmed;
}

function resolveQuestions(tool: CHAT.Task) {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  const fromMap =
    nested.questions || resultMap.questions || toolAny.questions || undefined;
  const fromMapParsed = normalizeAskQuestions(fromMap);
  if (fromMapParsed.length) return fromMapParsed;
  return parseAskInput(resolveTaskToolArg(tool));
}

function resolveAnswers(tool: CHAT.Task) {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  const fromMap = parseAskAnswersRecord({
    answers: nested.answers || resultMap.answers || toolAny.answers,
    note: nested.note || resultMap.note || toolAny.note,
  });
  if (fromMap.recognized) return fromMap;
  return parseAskOutput(resolveTaskToolOutput(tool));
}

export const AskUserToolCall = memo(function AskUserToolCall({
  tool,
  durationMs,
  durationLabel,
  stackPosition = "single",
  defaultExpanded,
}: AskUserToolCallProps) {
  const questions = useMemo(() => resolveQuestions(tool), [tool]);
  const output = useMemo(() => resolveAnswers(tool), [tool]);
  const status = resolveTaskToolStatus(tool);
  const timing = durationLabel || formatDurationLabel(durationMs);
  const rawLines = resolveTaskToolOutput(tool);

  const isDismissed =
    output.recognized &&
    Object.keys(output.answers).length === 0 &&
    output.note.length > 0;
  const answeredCount = Object.keys(output.answers).length;

  const resolved = useMemo(
    () =>
      questions.map((q, i) =>
        resolveAnswer(answerFor(output.answers, q.question, i), q.options)
      ),
    [questions, output.answers]
  );

  const summary = useMemo(() => {
    if (!output.recognized) return clip(rawLines[0] ?? "");
    if (isDismissed) return "已跳过";
    const first = questions[0]?.question ?? "";
    const base = clip(first);
    if (questions.length <= 1) return base;
    return `${base}  +${questions.length - 1}`;
  }, [output.recognized, isDismissed, questions, rawLines]);

  const chip = useMemo(() => {
    if (!output.recognized) return "";
    if (isDismissed) return "已跳过";
    if (answeredCount === 0) return "";
    return answeredCount === 1 ? "1 个回答" : `${answeredCount} 个回答`;
  }, [output.recognized, isDismissed, answeredCount]);

  const canExpand =
    (output.recognized && (questions.length > 0 || isDismissed)) ||
    rawLines.length > 0;
  const [open, setOpen] = useState(Boolean(defaultExpanded && canExpand));

  useEffect(() => {
    if (defaultExpanded && canExpand) setOpen(true);
  }, [defaultExpanded, canExpand, rawLines.length, status]);

  return (
    <ToolRow
      status={status}
      icon={<HelpCircleIcon className="size-3.5" />}
      name="提问"
      arg={!open ? summary : ""}
      time={timing}
      chip={chip}
      open={open}
      expandable={canExpand}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={() => {
        if (canExpand) setOpen((v) => !v);
      }}
    >
      {isDismissed ? (
        <div className="kimi-au-dismissed">{output.note}</div>
      ) : output.recognized ? (
        <div className="kimi-au-list">
          {questions.map((q, qi) => {
            const r = resolved[qi];
            return (
              <div key={`${q.question}-${qi}`} className="kimi-au-block">
                <div className="kimi-au-q">
                  {q.header ? <span className="kimi-au-hdr">{q.header}</span> : null}
                  <span className="kimi-au-qtext">{q.question}</span>
                </div>
                <div className="kimi-au-opts">
                  {q.options.map((opt, oi) => {
                    const on = r?.selected.has(oi) ?? false;
                    return (
                      <div
                        key={`${opt.label}-${oi}`}
                        className={cn("kimi-au-opt", on && "is-sel")}
                      >
                        <span className="kimi-au-glyph">
                          {q.multiSelect ? (on ? "■" : "□") : on ? "●" : "○"}
                        </span>
                        <span>{opt.label}</span>
                        {opt.description ? (
                          <span className="kimi-au-desc">{opt.description}</span>
                        ) : null}
                      </div>
                    );
                  })}
                  {r?.otherText ? (
                    <div className="kimi-au-opt is-sel">
                      <span className="kimi-au-glyph">
                        {q.multiSelect ? "■" : "●"}
                      </span>
                      <span>{r.otherText}</span>
                    </div>
                  ) : null}
                  {r?.indeterminate ? (
                    <div className="kimi-au-opt is-sel">
                      <span className="kimi-au-glyph">●</span>
                      <span>已回答</span>
                    </div>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="kimi-au-raw">
          {rawLines.map((line, i) => (
            <div key={`${i}-${line.slice(0, 12)}`}>{line}</div>
          ))}
        </div>
      )}
    </ToolRow>
  );
});
