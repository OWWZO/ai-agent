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

  it("requests recent sessions in pages of ten and forwards the cursor", async () => {
    await conversationHistoryApi.listSessions({
      limit: 10,
      after: "session-cursor-001",
    });

    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/conversation/sessions",
      {
        limit: 10,
        after: "session-cursor-001",
      }
    );
  });

  it("defaults the recent session page size to ten", async () => {
    await conversationHistoryApi.listSessions();

    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/conversation/sessions",
      { limit: 10 }
    );
  });

  it("requests one run replay by requestId", async () => {
    await conversationHistoryApi.getRunReplay("request-001");

    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/conversation/runs/request-001/replay"
    );
  });

  it("requests the encoded session file manifest", async () => {
    const files = [
      {
        displayName: "report.md",
        relativePath: "reports/report.md",
        resourceKey: "workspace/report.md",
      },
    ];
    getMock.mockResolvedValueOnce(files);

    await expect(
      conversationHistoryApi.getSessionFiles("session/with spaces")
    ).resolves.toEqual(files);
    expect(getMock).toHaveBeenCalledWith(
      "/api/agent/conversation/sessions/session%2Fwith%20spaces/files"
    );
  });
});
