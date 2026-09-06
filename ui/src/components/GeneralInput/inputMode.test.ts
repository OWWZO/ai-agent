import { describe, expect, it } from "vitest";

import { buildSubmitPayload } from "./inputMode";

describe("inputMode", () => {
  it("深度研究模式不再透传输出格式，仅打开 deepThink", () => {
    expect(
      buildSubmitPayload({
        question: "帮我调研竞品",
        visibleMode: "research",
        isDataAgent: false,
        uploadedFiles: [],
      })
    ).toMatchObject({
      deepThink: true,
    });
    expect(
      buildSubmitPayload({
        question: "帮我调研竞品",
        visibleMode: "research",
        isDataAgent: false,
        uploadedFiles: [],
      })
    ).not.toHaveProperty("outputStyle");
  });

  it("本轮计划开关只在显式打开时透传 forcePlanMode", () => {
    expect(
      buildSubmitPayload({
        question: "重新规划",
        visibleMode: "research",
        isDataAgent: false,
        uploadedFiles: [],
        forcePlanMode: true,
      })
    ).toMatchObject({
      deepThink: true,
      forcePlanMode: true,
    });
    expect(
      buildSubmitPayload({
        question: "继续执行",
        visibleMode: "research",
        isDataAgent: false,
        uploadedFiles: [],
      })
    ).not.toHaveProperty("forcePlanMode");
  });

  it("标准任务不透传 outputStyle", () => {
    expect(
      buildSubmitPayload({
        question: "先帮我分析这个问题",
        visibleMode: "think",
        isDataAgent: false,
        uploadedFiles: [],
      })
    ).toMatchObject({
      deepThink: false,
    });
    expect(
      buildSubmitPayload({
        question: "先帮我分析这个问题",
        visibleMode: "think",
        isDataAgent: false,
        uploadedFiles: [],
      })
    ).not.toHaveProperty("outputStyle");
  });

  it("思考开启但未指定强度时默认使用高", () => {
    expect(
      buildSubmitPayload({
        question: "分析这个问题",
        visibleMode: "think",
        isDataAgent: false,
        uploadedFiles: [],
        thinking: true,
      })
    ).toMatchObject({
      thinking: true,
      thinkingEffort: "high",
    });
  });
});
