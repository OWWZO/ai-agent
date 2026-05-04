import { describe, expect, it } from "vitest";

import {
  buildPlannerRoundsForDisplay,
  syncPlannerVersionCursor,
} from "./plannerHistory";

function createChat(overrides?: Partial<CHAT.ChatItem>): CHAT.ChatItem {
  return {
    sessionId: "session-planner-history",
    requestId: "req-planner-history",
    query: "测试 planner history",
    files: [],
    forceStop: false,
    loading: false,
    tasks: [],
    timeline: [],
    thought: "",
    multiAgent: {
      tasks: [],
    },
    ...overrides,
  } as CHAT.ChatItem;
}

describe("plannerHistory helpers", () => {
  it("plannerRounds 为空时，会回退到 latest alias 构造单轮显示数据", () => {
    const rounds = buildPlannerRoundsForDisplay(createChat({
      thought: "最新思考",
      plan: {
        title: "最新计划",
        stages: ["阶段一"],
        steps: ["步骤一"],
        stepStatus: ["in_progress"],
        notes: [""],
      },
      multiAgent: {
        tasks: [],
        plan_thought: "最新思考",
        plan: {
          title: "最新计划",
          stages: ["阶段一"],
          steps: ["步骤一"],
          stepStatus: ["in_progress"],
          notes: [""],
        },
      },
    }));

    expect(rounds).toHaveLength(1);
    expect(rounds[0].plannerRoundId).toBe("latest");
    expect(rounds[0].planThought).toBe("最新思考");
    expect(rounds[0].plan?.title).toBe("最新计划");
  });

  it("流式 thought 会覆盖最新轮次的显示文本，但不会改写历史轮次", () => {
    const rounds = buildPlannerRoundsForDisplay(createChat({
      multiAgent: {
        tasks: [],
        plannerRounds: [
          {
            plannerRoundId: "planner-round-1",
            planThought: "第一轮思考",
          },
          {
            plannerRoundId: "planner-round-2",
            planThought: "第二轮旧思考",
          },
        ],
      },
    }), "第二轮流式思考");

    expect(rounds).toHaveLength(2);
    expect(rounds[0].planThought).toBe("第一轮思考");
    expect(rounds[1].planThought).toBe("第二轮流式思考");
  });

  it("游标原本跟随最新时，新版本到达后会自动跳到最新；手动旧版本则保持原位", () => {
    expect(syncPlannerVersionCursor(1, 1, 2)).toBe(1);
    expect(syncPlannerVersionCursor(0, 2, 3)).toBe(0);
    expect(syncPlannerVersionCursor(9, 1, 2)).toBe(1);
    expect(syncPlannerVersionCursor(undefined, 1, 2)).toBe(1);
  });
});
