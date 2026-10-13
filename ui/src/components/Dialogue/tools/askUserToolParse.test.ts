import { describe, expect, it } from "vitest";
import {
  answerFor,
  askAnswerText,
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
        options: [{
          label: "React",
          description: "UI"
        }, { label: "Vue" }],
      },
    ]);
    expect(qs).toHaveLength(1);
    expect(qs[0].multiSelect).toBe(true);
    expect(qs[0].options[0].label).toBe("React");
  });

  it("parses ask input JSON arg", () => {
    const qs = parseAskInput(
      JSON.stringify({
        questions: [{
          question: "Q1",
          options: [{ label: "A" }]
        }],
      })
    );
    expect(qs[0].question).toBe("Q1");
  });

  it("recognizes answer payload and resolves labels", () => {
    const out = parseAskOutput([
      JSON.stringify({
        answers: { 选框架: "React, Vue" },
        note: ""
      }),
    ]);
    expect(out.recognized).toBe(true);
    const resolved = resolveAnswer(answerFor(out.answers, "选框架", 0), [
      {
        label: "React",
        description: ""
      },
      {
        label: "Vue",
        description: ""
      },
      {
        label: "Svelte",
        description: ""
      },
    ]);
    expect([...resolved.selected].sort()).toEqual([0, 1]);
  });

  it("falls back to raw for plain-text output", () => {
    const out = parseAskOutput(["task_id: abc\nstatus: running"]);
    expect(out.recognized).toBe(false);
  });
});

describe("askAnswerText（对齐 ZCode getAskUserQuestionAnswerText）", () => {
  const question = (labels: string[], multiSelect = false) => ({
    question: "选框架",
    header: "Tech",
    multiSelect,
    options: labels.map((label) => ({
      label,
      description: ""
    })),
  });

  it("未回答时回落到提示文案", () => {
    expect(askAnswerText(question(["React"]), undefined, "未提供回答")).toBe(
      "未提供回答"
    );
  });

  it("后端只回了「已答」事实时显示已回答", () => {
    expect(askAnswerText(question(["React"]), true, "未提供回答")).toBe("已回答");
  });

  it("答案整体命中 label 时原样输出", () => {
    expect(askAnswerText(question(["React", "Vue"]), "Vue", "未提供回答")).toBe(
      "Vue"
    );
  });

  it("opt_i_j 形式的 id 还原成对应 label", () => {
    expect(
      askAnswerText(question(["React", "Vue"]), "opt_0_1", "未提供回答")
    ).toBe("Vue");
  });

  it("多选用全角顿号连接", () => {
    expect(
      askAnswerText(
        question(["React", "Vue", "Svelte"], true),
        "React,Svelte",
        "未提供回答"
      )
    ).toBe("React，Svelte");
  });

  it("命中选项时带上 description —— 只回 label 看不出选了什么", () => {
    const q = {
      question: "请选择一个选项，测试 asktool 是否正常工作？",
      header: "测试",
      multiSelect: false,
      options: [
        { label: "选项 A", description: "第一条路径" },
        { label: "选项 B", description: "第二条路径" },
      ],
    };

    expect(askAnswerText(q, "选项 B", "未提供回答")).toBe("选项 B — 第二条路径");
  });

  it("选项 label 与自定义文本混排时都保留", () => {
    expect(
      askAnswerText(question(["React"]), "React,我自己写一个", "未提供回答")
    ).toBe("React，我自己写一个");
  });

  it("空字符串按未回答处理", () => {
    expect(askAnswerText(question(["React"]), "", "未提供回答")).toBe(
      "未提供回答"
    );
  });
});
