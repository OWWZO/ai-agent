import { describe, expect, it } from "vitest";

import { buildSubmitPayload } from "./inputMode";

describe("inputMode", () => {
  it("深度研究模式应保留结构化输出类型并打开 deepThink", () => {
    expect(
      buildSubmitPayload({
        question: "帮我调研竞品",
        visibleMode: "research",
        isDataAgent: false,
        currentProductType: "html",
        uploadedFiles: [],
        chatRole: null,
      })
    ).toMatchObject({
      outputStyle: "html",
      deepThink: true,
    });
  });

  it("未选择输出格式时不应默认透传 html", () => {
    expect(
      buildSubmitPayload({
        question: "先帮我分析这个问题",
        visibleMode: "think",
        isDataAgent: false,
        currentProductType: "task",
        uploadedFiles: [],
        chatRole: null,
      })
    ).toMatchObject({
      deepThink: false,
    });
    expect(
      buildSubmitPayload({
        question: "先帮我分析这个问题",
        visibleMode: "think",
        isDataAgent: false,
        currentProductType: "task",
        uploadedFiles: [],
        chatRole: null,
      })
    ).not.toHaveProperty("outputStyle");
  });
});
