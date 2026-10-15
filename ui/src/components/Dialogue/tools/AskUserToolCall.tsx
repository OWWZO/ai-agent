import { memo, useEffect, useMemo } from "react";
import { HelpCircleIcon } from "lucide-react";
import { ToolRow, type ToolRowStackPosition } from "./ToolRow";
import {
  answerFor,
  askAnswerText,
  normalizeAskQuestions,
  parseAskAnswersRecord,
  parseAskInput,
  parseAskOutput,
} from "./askUserToolParse";
import {
  resolveTaskToolArg,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
  resolveToolKey,
} from "./toolTaskAdapter";
import { useToolCardOpen } from "./useToolCardOpen";

type AskUserToolCallProps = {
  tool: CHAT.Task;
  stackPosition?: ToolRowStackPosition;
  defaultExpanded?: boolean;
};

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
  stackPosition = "single",
  defaultExpanded,
}: AskUserToolCallProps) {
  const questions = useMemo(() => resolveQuestions(tool), [tool]);
  const output = useMemo(() => resolveAnswers(tool), [tool]);
  const status = resolveTaskToolStatus(tool);
  const rawLines = resolveTaskToolOutput(tool);

  // ZCode `wasAutomaticallyContinued`：已跑完、非失败、answers 已识别但一条都没有。
  const hasNoAnswer =
    output.recognized && Object.keys(output.answers).length === 0;
  const noAnswerText = hasNoAnswer
    ? output.note || "未回答，已自动继续"
    : "未提供回答";

  const summary = useMemo(() => {
    // 对齐 ZCode `renderers/ask-question.tsx`：摘要行不放问句 ——
    // 自动继续 → 提示文案；否则 → 「N 个问题」（原版 secondaryText）。
    if (status === "running") return "";
    if (hasNoAnswer) return noAnswerText;
    return `${questions.length} 个问题`;
  }, [status, hasNoAnswer, noAnswerText, questions.length]);

  const canExpand = output.recognized || rawLines.length > 0;
  const [open, setOpen] = useToolCardOpen(
    resolveToolKey(tool),
    Boolean(defaultExpanded && canExpand)
  );

  useEffect(() => {
    if (defaultExpanded && canExpand) setOpen(true);
  }, [defaultExpanded, canExpand, rawLines.length, status, setOpen]);

  return (
    <ToolRow
      status={status}
      icon={<HelpCircleIcon className="size-4" />}
      // ZCode 的 kindLabel 随运行态切换：正在询问 / 已询问
      name={status === "running" ? "正在询问" : "已询问"}
      arg={!open ? summary : ""}
      animateSummary={status === "running"}
      open={open}
      expandable={canExpand}
      stacked={stackPosition !== "single"}
      stackPosition={stackPosition}
      onToggle={() => {
        if (canExpand) setOpen((v) => !v);
      }}
    >
      {output.recognized ? (
        // 对齐 ZCode `renderContent`：**只渲染题干 + 答案文本**，不铺选项列表。
        // 容器是 `ml-2 space-y-2 border-l pl-3.5` 的左侧导轨，每题 `space-y-1`。
        <div className="kimi-au-list">
          {questions.length === 0 ? (
            <p className="kimi-au-answer">{noAnswerText}</p>
          ) : (
            questions.map((q, qi) => (
              <div key={`${q.question}-${qi}`} className="kimi-au-block">
                <p className="kimi-au-qtext">{q.question}</p>
                <p className="kimi-au-answer">
                  {askAnswerText(
                    q,
                    answerFor(output.answers, q.question, qi),
                    noAnswerText
                  )}
                </p>
              </div>
            ))
          )}
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
