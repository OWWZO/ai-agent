import { describe, expect, it } from "vitest";

import { buildSubmitPayload } from "./inputMode";

describe("inputMode", () => {
  it("深度研究模式应保留结构化输出类型并打开 deepThink", () => {
    expect(
      buildSubmitPayload({
        question: "帮我调研竞品",
        visibleMode: "research",
        isDataAgent: false,
        visibleOutputProduct: { type: "html" } as CHAT.Product,
        uploadedFiles: [],
        chatRole: null,
      })
    ).toMatchObject({
      outputStyle: "html",
      deepThink: true,
    });
  });
});
