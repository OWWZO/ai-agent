import { describe, expect, it } from "vitest";

import { isDuplicateOfConclusion } from "./AgentStepTimeline";

describe("AgentStepTimeline", () => {
  it("把纯代码过程回复识别为与最终结论重复", () => {
    const code = '```python\nprint("你好，世界！")\n```';

    expect(
      isDuplicateOfConclusion(
        {
          type: "assistant_reply",
          text: code,
        } as never,
        code
      )
    ).toBe(true);
  });
});
