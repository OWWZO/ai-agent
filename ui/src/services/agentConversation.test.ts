import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.hoisted(() => vi.fn());

vi.mock("./index", () => ({default: {get: getMock,},}));

import { conversationHistoryApi } from "./agentConversation";

describe("conversationHistoryApi", () => {
  beforeEach(() => {
    getMock.mockReset();
    getMock.mockResolvedValue({});
  });

  it("requests a cursor page from the current session endpoint", async () => {
    await conversationHistoryApi.getSessionDetail("session-001", {
      limit: 20,
      after: "cursor-001",
    });

    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/conversation/sessions/session-001",
      {
        limit: 20,
        after: "cursor-001",
      }
    );
  });

  it("requests one run replay by requestId", async () => {
    await conversationHistoryApi.getRunReplay("request-001");

    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/conversation/runs/request-001/replay"
    );
  });
});
