import { beforeEach, describe, expect, it, vi } from "vitest";

const { fetchEventSourceMock } = vi.hoisted(() => ({ fetchEventSourceMock: vi.fn(), }));

vi.mock("@microsoft/fetch-event-source", () => ({ fetchEventSource: fetchEventSourceMock, }));

import querySSE from "./querySSE";

const createConfig = (overrides: Record<string, unknown> = {}) => ({
  body: { requestId: "request-1" },
  handleMessage: vi.fn(),
  handleError: vi.fn(),
  handleClose: vi.fn(),
  ...overrides,
});

describe("querySSE", () => {
  beforeEach(() => {
    fetchEventSourceMock.mockReset();
    fetchEventSourceMock.mockResolvedValue(undefined);
  });

  it("默认不重发 POST，并在页面隐藏时保持 SSE 连接", () => {
    const config = createConfig();

    querySSE(config);

    const options = fetchEventSourceMock.mock.calls[0][1] as {
      onerror: (error: Error) => unknown;
      openWhenHidden: boolean;
    };

    expect(options.openWhenHidden).toBe(true);
    expect(() => options.onerror(new Error("network disconnected"))).toThrow(
      "network disconnected"
    );
    expect(config.handleError).not.toHaveBeenCalled();
  });

  it("收到带 id 的事件时保存事件游标", () => {
    const handleEventId = vi.fn();
    const config = createConfig({ handleEventId });

    querySSE(config);

    const options = fetchEventSourceMock.mock.calls[0][1] as {
      onmessage: (event: { id: string; data: string }) => void;
    };
    options.onmessage({
      id: "event-7",
      data: JSON.stringify({ value: 1 }),
    });

    expect(handleEventId).toHaveBeenCalledWith("event-7");
    expect(config.handleMessage).toHaveBeenCalledWith({ value: 1 });
  });

  it("单条 SSE 帧解析失败不冒充连接断开", () => {
    const config = createConfig({
      parser: () => {
        throw new Error("invalid frame");
      },
    });

    querySSE(config);

    const options = fetchEventSourceMock.mock.calls[0][1] as {
      onmessage: (event: { id: string; data: string }) => void;
    };
    options.onmessage({ id: "event-invalid", data: JSON.stringify({ value: 1 }) });

    expect(config.handleError).not.toHaveBeenCalled();
  });

  it("主动 abort 不进入业务错误回调", async () => {
    fetchEventSourceMock.mockRejectedValueOnce(
      new DOMException("aborted", "AbortError")
    );
    const config = createConfig();

    querySSE(config);
    await Promise.resolve();

    expect(config.handleError).not.toHaveBeenCalled();
  });
});
