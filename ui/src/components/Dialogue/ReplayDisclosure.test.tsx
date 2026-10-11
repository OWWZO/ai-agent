import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import ReplayDisclosure, {
  createReplayRequestGate,
  shouldHydrateRunReplay,
} from "./ReplayDisclosure";

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
  options: {
    replayLoading?: boolean;
    onRequestReplay?: (requestId: string) => void | PromiseLike<unknown>;
    open?: boolean;
    onToggle?: (open: boolean) => void;
  } = {}
) {
  return renderToStaticMarkup(
    <ReplayDisclosure
      chat={chat}
      replayLoading={options.replayLoading ?? false}
      onRequestReplay={options.onRequestReplay ?? vi.fn()}
      open={options.open}
      onToggle={options.onToggle}
    />
  );
}

describe("ReplayDisclosure", () => {
  it("renders a full-width status row with the replay affordance", () => {
    const html = renderDisclosure(createChat(), { onToggle: vi.fn() });

    expect(html).toContain('data-testid="replay-disclosure"');
    expect(html).toContain('type="button"');
    expect(html).toContain("w-full");
    expect(html).toContain("已工作");
    expect(html).toContain('data-testid="replay-chevron"');
  });

  it("formats the run duration from runDurationMs", () => {
    const html = renderDisclosure(
      createChat({
        runDurationMs: 283_000,
        tasks: [[{ durationMs: 599_000 } as unknown as CHAT.Task]],
      })
    );

    expect(html).toContain("4 分 43 秒");
    expect(html).not.toContain("9 分 59 秒");
  });

  it.each([
    ["SUCCESS", "已工作"],
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
    const html = renderDisclosure(createChat(), {
      replayLoading: true,
      onToggle: vi.fn(),
    });

    expect(html).toContain("disabled");
    expect(html).toContain('data-testid="replay-loading"');
    expect(html).not.toContain('data-testid="replay-chevron"');
  });

  it("回放补齐后仍然常驻，作为可再次折叠的 trigger", () => {
    const html = renderDisclosure(createChat({ replayLoaded: true }), {
      onToggle: vi.fn(),
    });

    expect(html).toContain('data-testid="replay-disclosure"');
    expect(html).toContain('data-testid="replay-chevron"');
  });

  it("过程展开时 chevron 旋转 90 度，折叠时不旋转", () => {
    const expanded = renderDisclosure(createChat(), {
      open: true,
      onToggle: vi.fn(),
    });
    const collapsed = renderDisclosure(createChat(), {
      open: false,
      onToggle: vi.fn(),
    });

    expect(expanded).toContain("rotate-90");
    expect(expanded).toContain('data-process-open="true"');
    expect(collapsed).not.toContain("rotate-90");
    expect(collapsed).toContain('data-process-open="false"');
  });

  it("没有可折叠过程时不显示 chevron（退化为纯回放入口）", () => {
    const html = renderDisclosure(createChat());

    expect(html).toContain('data-testid="replay-disclosure"');
    expect(html).not.toContain('data-testid="replay-chevron"');
    expect(html).not.toContain("data-process-open");
  });

  it("运行中不渲染（由 TurnWorkStatus 接管）", () => {
    expect(renderDisclosure(createChat({ loading: true }))).toBe("");
  });

  it("后端不支持回放时不渲染", () => {
    expect(renderDisclosure(createChat({ replayAvailable: false }))).toBe("");
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
      { onRequestReplay: request }
    );

    expect(html).toContain("执行失败");
    expect(html).toContain("featured-run-1");
  });
});

describe("shouldHydrateRunReplay", () => {
  it("首次点击需要补回放", () => {
    expect(
      shouldHydrateRunReplay({
        attemptedRequestId: null,
        requestId: "run-1",
        replayLoaded: false,
        replayAvailable: true,
      })
    ).toBe(true);
  });

  it("同一 requestId 已尝试过就不再补 —— 折叠/展开不会重复打后端", () => {
    expect(
      shouldHydrateRunReplay({
        attemptedRequestId: "run-1",
        requestId: "run-1",
        replayLoaded: false,
        replayAvailable: true,
      })
    ).toBe(false);
  });

  it("回放已加载（replayLoaded）不再补", () => {
    expect(
      shouldHydrateRunReplay({
        attemptedRequestId: null,
        requestId: "run-1",
        replayLoaded: true,
        replayAvailable: true,
      })
    ).toBe(false);
  });

  it("后端不支持回放时不补", () => {
    expect(
      shouldHydrateRunReplay({
        attemptedRequestId: null,
        requestId: "run-1",
        replayAvailable: false,
      })
    ).toBe(false);
  });

  it("换一轮（不同 requestId）仍会补", () => {
    expect(
      shouldHydrateRunReplay({
        attemptedRequestId: "run-1",
        requestId: "run-2",
        replayLoaded: false,
        replayAvailable: true,
      })
    ).toBe(true);
  });

  it("没有 requestId 时不补", () => {
    expect(
      shouldHydrateRunReplay({ attemptedRequestId: null, requestId: "" })
    ).toBe(false);
  });
});
