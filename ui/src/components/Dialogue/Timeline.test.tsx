import { describe, expect, it } from "vitest";
import { isAnsweredAskUserTask } from "./timelineAskUser";

describe("AskUserQuestion timeline status", () => {
  it("treats persisted uppercase answered status as completed", () => {
    expect(
      isAnsweredAskUserTask({
        messageType: "ask_user_question",
        resultMap: { status: "ANSWERED" },
      } as CHAT.Task)
    ).toBe(true);
  });
});
