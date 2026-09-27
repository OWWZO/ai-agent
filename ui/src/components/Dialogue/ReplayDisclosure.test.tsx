import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import ReplayDisclosure, { createReplayRequestGate } from "./ReplayDisclosure";

function createChat(overrides: Partial<CHAT.ChatItem> = {}): CHAT.ChatItem {
  return {
    sessionId: "session-1",
    requestId: "run-1",
    query: "问题",
    files: [],
    forceStop: false,
    multiAgent: { tasks: [] },
    loading: false,
    tasks: [],
    replayAvailable: true,
    replayLoaded: false,
    metrics: { status: "SUCCESS" },
    ...overrides,
  } as CHAT.ChatItem;
}

function renderDisclosure(
  chat: CHAT.ChatItem,
  replayLoading = false,
  onRequestReplay = vi.fn()
) {
  return renderToStaticMarkup(
    <ReplayDisclosure
      chat={chat}
      replayLoading={replayLoading}
      onRequestReplay={onRequestReplay}
    />
  );
}

describe("ReplayDisclosure", () => {
  it("renders a full-width status row with the replay affordance", () => {
    const html = renderDisclosure(createChat());

    expect(html).toContain('data-testid="replay-disclosure"');
    expect(html).toContain('type="button"');
    expect(html).toContain("w-full");
    expect(html).toContain("已完成");
    expect(html).toContain('data-testid="replay-chevron"');
  });

  it("formats the run duration from runDurationMs", () => {
    const html = renderDisclosure(
      createChat({
        runDurationMs: 283_000,
        tasks: [[{ durationMs: 599_000 } as unknown as CHAT.Task]],
      })
    );

    expect(html).toContain("4m43s");
    expect(html).not.toContain("9m59s");
  });

  it.each([
    ["SUCCESS", "已完成"],
    ["FAILED", "执行失败"],
    ["STOPPED", "已停止"],
    ["TIMEOUT", "已超时"],
    ["WAITING_INPUT", "等待回答"],
  ])("uses distinct copy for %s", (status, label) => {
    const html = renderDisclosure(
      createChat({ metrics: { status } })
    );

    expect(html).toContain(label);
  });

  it("locks the row while replay is loading and replaces the chevron", () => {
    const html = renderDisclosure(createChat(), true);

    expect(html).toContain("disabled");
    expect(html).toContain('data-testid="replay-loading"');
    expect(html).not.toContain('data-testid="replay-chevron"');
  });

  it("hides after replay has been loaded", () => {
    expect(renderDisclosure(createChat({ replayLoaded: true }))).toBe("");
  });

  it("accepts one request at a time and can retry after failure", async () => {
    let rejectRequest!: (reason?: unknown) => void;
    const pendingRequest = new Promise<void>((_, reject) => {
      rejectRequest = reject;
    });
    const request = vi
      .fn<(requestId: string) => Promise<void>>()
      .mockReturnValueOnce(pendingRequest)
      .mockResolvedValueOnce();
    const gate = createReplayRequestGate();

    expect(gate.request("run-1", request)).toBe(true);
    expect(gate.request("run-1", request)).toBe(false);
    expect(request).toHaveBeenCalledTimes(1);

    rejectRequest(new Error("replay failed"));
    await Promise.resolve();

    expect(gate.request("run-1", request)).toBe(true);
    expect(request).toHaveBeenCalledTimes(2);
  });

  it("keeps the same disclosure contract for featured conversation runs", () => {
    const request = vi.fn();
    const html = renderDisclosure(
      createChat({
        sessionId: "featured-session",
        requestId: "featured-run-1",
        metrics: { status: "FAILED" },
      }),
      false,
      request
    );

    expect(html).toContain("执行失败");
    expect(html).toContain("featured-run-1");
  });
});
