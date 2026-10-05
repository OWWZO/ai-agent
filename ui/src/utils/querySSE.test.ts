import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearAuthSession, setAuthSession } from "@/stores/auth";

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
    clearAuthSession();
  });

  it("每次建立 SSE 时读取当前内存 access token", () => {
    setAuthSession("access-token-1", null);
    querySSE(createConfig());
    const firstOptions = fetchEventSourceMock.mock.calls[0][1] as {
      headers: Record<string, string>;
    };
    expect(firstOptions.headers.Authorization).toBe("Bearer access-token-1");

    setAuthSession("access-token-2", null);
    querySSE(createConfig());
    const secondOptions = fetchEventSourceMock.mock.calls[1][1] as {
      headers: Record<string, string>;
    };
    expect(secondOptions.headers.Authorization).toBe("Bearer access-token-2");
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

  it("允许幂等的 GET 观察流在页面隐藏时暂停", () => {
    querySSE(
      createConfig({
        method: "GET",
        openWhenHidden: false,
      })
    );

    const options = fetchEventSourceMock.mock.calls[0][1] as {
      openWhenHidden: boolean;
    };

    expect(options.openWhenHidden).toBe(false);
  });

  it("HTTP SSE 响应建立后通知调用方", () => {
    const handleOpen = vi.fn();
    const config = createConfig({ handleOpen });

    querySSE(config);

    const options = fetchEventSourceMock.mock.calls[0][1] as {
      onopen: (response: Response) => void;
    };
    options.onopen(
      new Response(null, {
        status: 200,
        headers: { "Content-Type": "text/event-stream" },
      })
    );

    expect(handleOpen).toHaveBeenCalledTimes(1);
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
    options.onmessage({
      id: "event-invalid",
      data: JSON.stringify({ value: 1 }),
    });

    expect(config.handleError).not.toHaveBeenCalled();
  });

  it("GET 续接不带 body，并写入 Last-Event-ID", () => {
    const config = createConfig({
      method: "GET",
      lastEventId: "12",
      body: null,
    });

    querySSE(config, "http://localhost/api/agent/session/s1/stream?lastEventSeq=12");

    const [url, options] = fetchEventSourceMock.mock.calls[0] as [
      string,
      { method: string; body?: string; headers: Record<string, string> },
    ];
    expect(url).toContain("/api/agent/session/s1/stream");
    expect(options.method).toBe("GET");
    expect(options.body).toBeUndefined();
    expect(options.headers["Last-Event-ID"]).toBe("12");
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
