import { describe, expect, it } from "vitest";
import {
  answerFor,
  normalizeAskQuestions,
  parseAskInput,
  parseAskOutput,
  resolveAnswer,
} from "./askUserToolParse";

describe("askUserToolParse", () => {
  it("parses questions with multi_select alias", () => {
    const qs = normalizeAskQuestions([
      {
        question: "选框架",
        header: "Tech",
        multi_select: true,
        options: [{ label: "React", description: "UI" }, { label: "Vue" }],
      },
    ]);
    expect(qs).toHaveLength(1);
    expect(qs[0].multiSelect).toBe(true);
    expect(qs[0].options[0].label).toBe("React");
  });

  it("parses ask input JSON arg", () => {
    const qs = parseAskInput(
      JSON.stringify({
        questions: [{ question: "Q1", options: [{ label: "A" }] }],
      })
    );
    expect(qs[0].question).toBe("Q1");
  });

  it("recognizes answer payload and resolves labels", () => {
    const out = parseAskOutput([
      JSON.stringify({ answers: { 选框架: "React, Vue" }, note: "" }),
    ]);
    expect(out.recognized).toBe(true);
    const resolved = resolveAnswer(answerFor(out.answers, "选框架", 0), [
      { label: "React", description: "" },
      { label: "Vue", description: "" },
      { label: "Svelte", description: "" },
    ]);
    expect([...resolved.selected].sort()).toEqual([0, 1]);
  });

  it("falls back to raw for plain-text output", () => {
    const out = parseAskOutput(["task_id: abc\nstatus: running"]);
    expect(out.recognized).toBe(false);
  });
});
